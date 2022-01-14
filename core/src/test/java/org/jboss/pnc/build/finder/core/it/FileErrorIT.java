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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.jboss.pnc.build.finder.core.ChecksumType.sha256;

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
import org.jboss.pnc.build.finder.core.LocalFile;
import org.jboss.pnc.build.finder.koji.ClientSession;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;

class FileErrorIT extends AbstractKojiIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileErrorIT.class);

    private static final String URL = "https://repo1.maven.org/maven2/jboss/jaxbintros/jboss-jaxb-intros/1.0.2.GA/jboss-jaxb-intros-1.0.2.GA-sources.jar";

    @Test
    void testChecksumsAndFindBuilds(@TempDir File folder) throws ExecutionException, InterruptedException {
        Timer timer = REGISTRY.timer(MetricRegistry.name(FileErrorIT.class, "checksums"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        DistributionAnalyzer analyzer;
        Future<Map<ChecksumType, MultiValuedMap<String, LocalFile>>> futureChecksum;

        try (Context ignored = timer.time()) {
            analyzer = new DistributionAnalyzer(Collections.singletonList(URL), getConfig());
            futureChecksum = pool.submit(analyzer);
        }

        Timer timer2 = REGISTRY.timer(MetricRegistry.name(FileErrorIT.class, "builds"));

        try (Context ignored = timer2.time()) {
            ClientSession session = getSession();
            BuildFinder finder = new BuildFinder(session, getConfig(), analyzer, null, getPncClient());
            finder.setOutputDirectory(folder);
            Future<Map<BuildSystemInteger, KojiBuild>> futureBuilds = pool.submit(finder);
            Map<ChecksumType, MultiValuedMap<String, LocalFile>> map = futureChecksum.get();
            Map<BuildSystemInteger, KojiBuild> builds = futureBuilds.get();
            Collection<FileError> fileErrors = analyzer.getFileErrors();
            Map<String, Collection<Checksum>> files = analyzer.getFiles();
            Map<Checksum, Collection<String>> foundChecksums = finder.getFoundChecksums();
            Map<Checksum, Collection<String>> notFoundChecksums = finder.getNotFoundChecksums();
            List<KojiBuild> buildsFound = finder.getBuildsFound();

            assertThat(map).hasSize(3);
            assertThat(builds).hasSize(2);
            assertThat(fileErrors).hasSize(1)
                    .extracting("filename", "message")
                    .containsExactly(tuple("jboss-jaxb-intros-1.0.2.GA-sources.jar", "Invalid relative file name."));
            assertThat(files).hasSize(1)
                    .hasEntrySatisfying(
                            "jboss-jaxb-intros-1.0.2.GA-sources.jar",
                            cksums -> assertThat(cksums).hasSize(3)
                                    .extracting("filename")
                                    .containsOnly("jboss-jaxb-intros-1.0.2.GA-sources.jar"));
            assertThat(analyzer.getChecksums(ChecksumType.md5)).hasSize(1)
                    .hasEntrySatisfying(
                            "ac2a6ab1fbf6afba37789e2e88a916a6",
                            cksums -> assertThat(cksums).extracting("filename", "size")
                                    .containsExactly(tuple("jboss-jaxb-intros-1.0.2.GA-sources.jar", 29537L)));
            assertThat(analyzer.getChecksums(ChecksumType.sha1)).hasSize(1)
                    .hasEntrySatisfying(
                            "ab2f490dd83035bee3a719d2118cbab90508082f",
                            cksums -> assertThat(cksums).singleElement()
                                    .extracting("filename", "size")
                                    .containsExactly("jboss-jaxb-intros-1.0.2.GA-sources.jar", 29537L));
            assertThat(analyzer.getChecksums(sha256)).hasSize(1)
                    .hasEntrySatisfying(
                            "987dd27e51ba77cb067dbec1baa5169eb184313688640e3951e3cb34d9a85c48",
                            cksums -> assertThat(cksums).singleElement()
                                    .extracting("filename", "size")
                                    .containsExactly("jboss-jaxb-intros-1.0.2.GA-sources.jar", 29537L));
            assertThat(notFoundChecksums).isEmpty();
            assertThat(foundChecksums).hasSize(1);
            assertThat(buildsFound).hasSize(1);
            assertThat(builds.get(new BuildSystemInteger(0)).getArchives()).isEmpty();

            LOGGER.info("Map size: {}", map.size());
            LOGGER.info("Builds size: {}", builds.size());
            LOGGER.info("File errors: {}", fileErrors.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}
