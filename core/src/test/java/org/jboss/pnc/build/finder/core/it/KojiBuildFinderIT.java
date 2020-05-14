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
package org.jboss.pnc.build.finder.core.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.io.FileUtils;
import org.jboss.pnc.build.finder.core.BuildFinder;
import org.jboss.pnc.build.finder.core.BuildSystemInteger;
import org.jboss.pnc.build.finder.core.ChecksumType;
import org.jboss.pnc.build.finder.core.DistributionAnalyzer;
import org.jboss.pnc.build.finder.koji.ClientSession;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

public class KojiBuildFinderIT extends AbstractKojiIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(KojiBuildFinderIT.class);

    private static final String PROPERTY = "distribution.url";

    private static final String URL = System.getProperty(PROPERTY);

    private static final int CONNECTION_TIMEOUT = 300000;

    private static final int READ_TIMEOUT = 900000;

    @Test
    public void testChecksumsAndFindBuilds(@TempDir File folder1, @TempDir File folder2)
            throws IOException, ExecutionException {
        assertNotNull(
                "You must set the property " + PROPERTY + " pointing to the URL of the distribution to test with",
                URL);

        final URL url = new URL(URL);
        final File file = new File(folder1, url.getPath());

        FileUtils.copyURLToFile(url, file, CONNECTION_TIMEOUT, READ_TIMEOUT);

        final Timer timer = REGISTRY.timer(MetricRegistry.name(KojiBuildFinderIT.class, "checksums"));

        final Timer.Context context = timer.time();

        final Map<ChecksumType, MultiValuedMap<String, String>> map;

        final ExecutorService pool = Executors.newFixedThreadPool(1 + getConfig().getChecksumTypes().size());

        final DistributionAnalyzer analyzer;

        final Future<Map<ChecksumType, MultiValuedMap<String, String>>> futureChecksum;

        try {
            analyzer = new DistributionAnalyzer(Collections.singletonList(file), getConfig());
            futureChecksum = pool.submit(analyzer);
        } finally {
            context.stop();
        }

        final Timer timer2 = REGISTRY.timer(MetricRegistry.name(KojiBuildFinderIT.class, "builds"));

        final Timer.Context context2 = timer2.time();

        try {
            final ClientSession session = getKojiClientSession();
            final BuildFinder finder = new BuildFinder(session, getConfig(), analyzer, null, getPncClient());
            finder.setOutputDirectory(folder2);
            Future<Map<BuildSystemInteger, KojiBuild>> futureBuilds = pool.submit(finder);
            Map<BuildSystemInteger, KojiBuild> builds = futureBuilds.get();
            map = futureChecksum.get();

            assertEquals(3, map.size());
            assertTrue(builds.size() >= 1);

            LOGGER.info("Map size: {}", map.size());
            LOGGER.info("Builds size: {}", builds.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            context2.stop();
        }
    }
}
