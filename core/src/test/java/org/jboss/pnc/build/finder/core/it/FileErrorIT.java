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

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections4.MultiValuedMap;
import org.jboss.pnc.build.finder.core.BuildFinder;
import org.jboss.pnc.build.finder.core.BuildSystemInteger;
import org.jboss.pnc.build.finder.core.Checksum;
import org.jboss.pnc.build.finder.core.ChecksumType;
import org.jboss.pnc.build.finder.core.DistributionAnalyzer;
import org.jboss.pnc.build.finder.core.FileError;
import org.jboss.pnc.build.finder.koji.ClientSession;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

class FileErrorIT extends AbstractKojiIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileErrorIT.class);

    private static final String URL = "https://repo1.maven.org/maven2/jboss/jaxbintros/jboss-jaxb-intros/1.0.2.GA/jboss-jaxb-intros-1.0.2.GA-sources.jar";

    private static final int CONNECTION_TIMEOUT = 300000;

    private static final int READ_TIMEOUT = 900000;

    @Test
    void testChecksumsAndFindBuilds(@TempDir File folder) throws ExecutionException {
        Timer timer = REGISTRY.timer(MetricRegistry.name(FileErrorIT.class, "checksums"));
        ExecutorService pool = Executors.newFixedThreadPool(1 + getConfig().getChecksumTypes().size());
        DistributionAnalyzer analyzer;
        Future<Map<ChecksumType, MultiValuedMap<String, String>>> futureChecksum;

        try (Timer.Context context = timer.time()) {
            analyzer = new DistributionAnalyzer(Collections.singletonList(URL), getConfig());
            futureChecksum = pool.submit(analyzer);
        }

        Timer timer2 = REGISTRY.timer(MetricRegistry.name(FileErrorIT.class, "builds"));

        try (Timer.Context context2 = timer2.time()) {
            ClientSession session = getSession();
            BuildFinder finder = new BuildFinder(session, getConfig(), analyzer, null, getPncClient());
            finder.setOutputDirectory(folder);
            Future<Map<BuildSystemInteger, KojiBuild>> futureBuilds = pool.submit(finder);
            Map<ChecksumType, MultiValuedMap<String, String>> map = futureChecksum.get();
            Map<BuildSystemInteger, KojiBuild> builds = futureBuilds.get();
            Map<BuildSystemInteger, KojiBuild> buildsMap = finder.getBuildsMap();
            Collection<FileError> fileErrors = analyzer.getFileErrors();
            Map<String, Collection<Checksum>> files = analyzer.getFiles();
            Map<Checksum, Collection<String>> foundChecksums = finder.getFoundChecksums();
            Map<Checksum, Collection<String>> notFoundChecksums = finder.getNotFoundChecksums();
            List<KojiBuild> buildsFound = finder.getBuildsFound();

            assertThat(map, is(aMapWithSize(3)));
            assertThat(builds, is(aMapWithSize(2)));
            assertThat(
                    fileErrors,
                    is(
                            allOf(
                                    hasSize(1),
                                    contains(
                                            allOf(
                                                    hasProperty(
                                                            "filename",
                                                            is("jboss-jaxb-intros-1.0.2.GA-sources.jar")),
                                                    hasProperty("message", is("Invalid relative file name.")))))));
            assertThat(
                    files,
                    hasEntry(
                            is("jboss-jaxb-intros-1.0.2.GA-sources.jar"),
                            allOf(
                                    hasSize(3),
                                    everyItem(hasProperty("filename", is("jboss-jaxb-intros-1.0.2.GA-sources.jar"))))));
            assertThat(
                    analyzer.getChecksums(ChecksumType.md5),
                    hasEntry(
                            is("ac2a6ab1fbf6afba37789e2e88a916a6"),
                            contains("jboss-jaxb-intros-1.0.2.GA-sources.jar")));
            assertThat(
                    analyzer.getChecksums(ChecksumType.sha1),
                    hasEntry(
                            is("ab2f490dd83035bee3a719d2118cbab90508082f"),
                            contains("jboss-jaxb-intros-1.0.2.GA-sources.jar")));
            assertThat(
                    analyzer.getChecksums(ChecksumType.sha256),
                    hasEntry(
                            is("987dd27e51ba77cb067dbec1baa5169eb184313688640e3951e3cb34d9a85c48"),
                            contains("jboss-jaxb-intros-1.0.2.GA-sources.jar")));
            assertThat(notFoundChecksums, is(anEmptyMap()));
            assertThat(foundChecksums, is(aMapWithSize(1)));
            assertThat(buildsFound, hasSize(1));
            assertThat(builds.get(new BuildSystemInteger(0)).getArchives(), hasSize(0));

            LOGGER.info("Map size: {}", map.size());
            LOGGER.info("Builds size: {}", builds.size());
            LOGGER.info("File errors: {}", fileErrors.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
