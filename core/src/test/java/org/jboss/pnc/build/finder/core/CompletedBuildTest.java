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
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildState;

@ExtendWith(WiremockExtension.class)
class CompletedBuildTest {
    @Wiremock
    private final WireMockServer server = new WireMockServer(
            WireMockConfiguration.options().usingFilesUnderClasspath("completed-build-test").dynamicPort());

    @Test
    void testCompletedBuilds() throws KojiClientException, MalformedURLException {
        Checksum checksum1 = new Checksum(
                ChecksumType.md5,
                "46148535be98c75c900837ecea491c71",
                "hibernate-validator-6.0.10.Final-redhat-1.pom",
                19544L);
        Checksum checksum2 = new Checksum(
                ChecksumType.md5,
                "c723630b4a215ffa05106e5c8555871c",
                "hibernate-validator-cdi-6.0.10.Final-redhat-1.pom",
                8591L);
        Collection<String> filenames1 = Collections.singletonList("hibernate-validator-6.0.10.Final-redhat-1.pom");
        Collection<String> filenames2 = Collections.singletonList("hibernate-validator-cdi-6.0.10.Final-redhat-1.pom");
        Map<Checksum, Collection<String>> checksumTable = new LinkedHashMap<>(2, 1.0f);

        checksumTable.put(checksum1, filenames1);
        checksumTable.put(checksum2, filenames2);

        BuildConfig config = new BuildConfig();

        config.setKojiHubURL(new URL(server.baseUrl()));

        try (KojiClientSession session = new KojiClientSession(config.getKojiHubURL())) {
            BuildFinder finder = new BuildFinder(session, config);
            Map<BuildSystemInteger, KojiBuild> builds = finder.getKojiBuildFinder().findBuilds(checksumTable);

            assertThat(builds).hasSize(2);
            assertThat(builds).hasEntrySatisfying(
                    new BuildSystemInteger(0),
                    build -> assertThat(Integer.parseInt(build.getId())).isZero());
            assertThat(builds).hasEntrySatisfying(
                    new BuildSystemInteger(700821, BuildSystem.koji),
                    build -> assertThat(build.getBuildInfo().getBuildState()).isEqualTo(KojiBuildState.COMPLETE));
        }
    }
}
