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
package org.jboss.pnc.build.finder.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiClientSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.redhat.red.build.koji.KojiClientException;

class EmptyBuildsTest extends AbstractWireMockTest {
    @RegisterExtension
    private static final WireMockExtension WIRE_MOCK_EXTENSION = newWireMockExtensionForClass(EmptyBuildsTest.class);

    private static BuildConfig config;

    @BeforeAll
    static void setup() throws MalformedURLException {
        config = new BuildConfig();
        config.setKojiHubURL(new URL(WIRE_MOCK_EXTENSION.baseUrl()));
    }

    @Test
    void testEmptyChecksums() throws IOException, KojiClientException {
        DistributionAnalyzer da = new DistributionAnalyzer(Collections.emptyList(), config);

        da.checksumFiles();

        try (KojiClientSession session = new KojiClientSession(config.getKojiHubURL())) {
            BuildFinder finder = new BuildFinder(session, config);
            Map<BuildSystemInteger, KojiBuild> builds = finder.findBuilds(Collections.emptyMap());

            assertThat(builds).isEmpty();
        }
    }

    @Test
    void testEmptyBuilds() throws KojiClientException {
        Map<Checksum, Collection<String>> checksumTable = getChecksumTable();

        try (KojiClientSession session = new KojiClientSession(config.getKojiHubURL())) {
            BuildFinder finder = new BuildFinder(session, config);
            Map<BuildSystemInteger, KojiBuild> builds = finder.findBuilds(checksumTable);

            assertThat(builds).hasSize(1);
            assertThat(builds).hasEntrySatisfying(
                    new BuildSystemInteger(0),
                    build -> assertThat(Integer.parseInt(build.getId())).isZero());
        }
    }

    @Override
    Map<Checksum, Collection<String>> getChecksumTable() {
        Checksum checksum1 = new Checksum(
                ChecksumType.md5,
                "ca5330166ccd4e2b205bed4b88f924b0",
                "random.jar!random.jar",
                -1L);
        Checksum checksum2 = new Checksum(ChecksumType.md5, "b3ba80c13aa555c3eb428dbf62e2c48e", "random.jar", -1L);
        Collection<String> filenames1 = Collections.singletonList("random.jar!random.jar");
        Collection<String> filenames2 = Collections.singletonList("random.jar");
        Map<Checksum, Collection<String>> checksumTable = new LinkedHashMap<>(2, 1.0f);

        checksumTable.put(checksum1, filenames1);
        checksumTable.put(checksum2, filenames2);
        return checksumTable;
    }

    @Test
    void testSkipOnEmptyZipFile() throws KojiClientException {
        // 76cdb2bad9582d23c1f6f4d868218d6c is md5 for empty zip file with size of 22 bytes
        String filename = "empty.zip";
        Collection<String> filenames = Collections.singletonList(filename);
        Checksum checksum = new Checksum(ChecksumType.md5, "76cdb2bad9582d23c1f6f4d868218d6c", filename, 22);

        try (KojiClientSession session = new KojiClientSession(config.getKojiHubURL())) {
            BuildFinderUtils utils = new BuildFinderUtils(config, null, session);
            assertThat(utils.isEmptyZipDigest(checksum)).isTrue();
            assertThat(utils.shouldSkipChecksum(checksum, filenames)).isTrue();
        }
    }

    @Test
    void testSkipExtension() throws KojiClientException {
        String filename = "file.foo";
        String extension = FilenameUtils.getExtension(filename);

        assertThat(config.getArchiveExtensions()).doesNotContain(extension);

        Collection<String> filenames = Collections.singletonList(filename);
        Checksum checksum = new Checksum(ChecksumType.md5, "b3ba80c13aa555c3eb428dbf62e2c48e", filename, -1L);

        try (KojiClientSession session = new KojiClientSession(config.getKojiHubURL())) {
            BuildFinderUtils utils = new BuildFinderUtils(config, null, session);
            assertThat(utils.shouldSkipChecksum(checksum, filenames)).isTrue();
        }
    }

    @Test
    void testNotSkipExtension() throws KojiClientException {
        String filename = "file.dll";
        String extension = FilenameUtils.getExtension(filename);

        assertThat(config.getArchiveExtensions()).contains(extension);

        Collection<String> filenames = Collections.singletonList(filename);
        Checksum checksum = new Checksum(ChecksumType.md5, "b3ba80c13aa555c3eb428dbf62e2c48e", filename, -1L);

        try (KojiClientSession session = new KojiClientSession(config.getKojiHubURL())) {
            BuildFinderUtils utils = new BuildFinderUtils(config, null, session);
            assertThat(utils.shouldSkipChecksum(checksum, filenames)).isFalse();
        }
    }
}
