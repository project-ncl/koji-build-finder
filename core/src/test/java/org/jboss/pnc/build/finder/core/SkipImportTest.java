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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiClientSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.sparkmuse.wiremock.Wiremock;
import com.github.sparkmuse.wiremock.WiremockExtension;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.redhat.red.build.koji.KojiClientException;

@ExtendWith(WiremockExtension.class)
class SkipImportTest {
    @Wiremock
    private final WireMockServer server = new WireMockServer(
            WireMockConfiguration.options().usingFilesUnderClasspath("skip-import-test").dynamicPort());

    @Test
    void testMultiImportsKeepEarliest() throws KojiClientException, MalformedURLException {
        Checksum checksum1 = new Checksum(
                ChecksumType.md5,
                "2e7e85f0ee97afde716231a6c792492a",
                "commons-lang-2.6-redhat-2.jar",
                287477L);
        Checksum checksum2 = new Checksum(
                ChecksumType.md5,
                "3b6a309e0dd4f488fd0cce429b44d067",
                "commons-lang-2.6-redhat-2.pom",
                17931L);
        List<String> filenames1 = Collections.singletonList("commons-lang-2.6-redhat-2.jar");
        List<String> filenames2 = Collections.singletonList("commons-lang-2.6-redhat-2.pom");
        Map<Checksum, Collection<String>> checksumTable = new LinkedHashMap<>(2, 1.0f);

        checksumTable.put(checksum1, filenames1);
        checksumTable.put(checksum2, filenames2);

        BuildConfig config = new BuildConfig();

        config.setKojiHubURL(new URL(server.baseUrl()));

        try (KojiClientSession session = new KojiClientSession(config.getKojiHubURL())) {
            BuildFinder finder = new BuildFinder(session, config);
            Map<BuildSystemInteger, KojiBuild> builds = finder.getKojiBuildFinder().findBuilds(checksumTable);

            assertThat(builds).hasSize(2)
                    .hasEntrySatisfying(
                            new BuildSystemInteger(0),
                            build -> assertThat(Integer.parseInt(build.getId())).isZero())
                    .hasEntrySatisfying(
                            new BuildSystemInteger(228994, BuildSystem.koji),
                            build -> assertThat(build.getId()).isEqualTo("228994"));
            assertThat(builds).doesNotContainKey(new BuildSystemInteger(251444, BuildSystem.koji));
        }
    }
}
