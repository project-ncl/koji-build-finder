/*
 * Copyright (C) 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.build.finder.report.it;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.io.FileMatchers.aReadableFile;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections4.MultiValuedMap;
import org.jboss.pnc.build.finder.core.BuildFinder;
import org.jboss.pnc.build.finder.core.BuildSystemInteger;
import org.jboss.pnc.build.finder.core.ChecksumType;
import org.jboss.pnc.build.finder.core.DistributionAnalyzer;
import org.jboss.pnc.build.finder.core.it.AbstractKojiIT;
import org.jboss.pnc.build.finder.koji.ClientSession;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.report.Report;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

class ReportIT extends AbstractKojiIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportIT.class);

    private static final String PROPERTY = "distribution.url";

    private static final String URL = System.getProperty(PROPERTY);

    private static final int CONNECTION_TIMEOUT = 300000;

    private static final int READ_TIMEOUT = 900000;

    @Test
    void testChecksumsAndFindBuildsAndGenerateReports(@TempDir File folder)
            throws ExecutionException, InterruptedException, IOException {
        assertThat(
                "You must set the property " + PROPERTY + " pointing to the URL of the distribution to test with",
                URL,
                is(notNullValue()));

        Timer timer = REGISTRY.timer(MetricRegistry.name(ReportIT.class, "checksums"));

        Map<ChecksumType, MultiValuedMap<String, String>> map;

        ExecutorService pool = Executors.newFixedThreadPool(2);

        DistributionAnalyzer analyzer;

        Future<Map<ChecksumType, MultiValuedMap<String, String>>> futureChecksum;

        try (Timer.Context context = timer.time()) {
            analyzer = new DistributionAnalyzer(Collections.singletonList(URL), getConfig());
            futureChecksum = pool.submit(analyzer);
        }

        Timer timer2 = REGISTRY.timer(MetricRegistry.name(ReportIT.class, "builds"));

        try (Timer.Context context2 = timer2.time()) {
            ClientSession session = getSession();
            BuildFinder finder = new BuildFinder(session, getConfig(), analyzer, null, getPncClient());
            finder.setOutputDirectory(folder);
            Future<Map<BuildSystemInteger, KojiBuild>> futureBuilds = pool.submit(finder);
            Map<BuildSystemInteger, KojiBuild> builds = futureBuilds.get();
            map = futureChecksum.get();

            assertThat(map, is(aMapWithSize(3)));
            assertThat(builds, is(aMapWithSize(greaterThanOrEqualTo(1))));

            LOGGER.info("Map size: {}", map.size());
            LOGGER.info("Builds size: {}", builds.size());

            Report.generateReports(getConfig(), finder.getBuilds(), finder.getOutputDirectory(), analyzer.getInputs());

            File nvrTxt = new File(finder.getOutputDirectory(), "nvr.txt");

            assertThat(nvrTxt, is(aReadableFile()));

            File gavTxt = new File(finder.getOutputDirectory(), "gav.txt");

            assertThat(gavTxt, is(aReadableFile()));

            File outputHtml = new File(finder.getOutputDirectory(), "output.html");

            assertThat(outputHtml, is(aReadableFile()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}
