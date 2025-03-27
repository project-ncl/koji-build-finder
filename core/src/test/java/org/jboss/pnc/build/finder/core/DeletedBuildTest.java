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
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiClientSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.redhat.red.build.koji.KojiClientException;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildState;

class DeletedBuildTest extends AbstractWireMockTest {
    @RegisterExtension
    private static final WireMockExtension WIRE_MOCK_EXTENSION = newWireMockExtensionForClass(DeletedBuildTest.class);

    private static BuildConfig config;

    @BeforeAll
    static void setup() throws MalformedURLException {
        config = new BuildConfig();
        config.setKojiHubURL(URI.create(WIRE_MOCK_EXTENSION.baseUrl()).toURL());
    }

    @Test
    void testDeletedBuild() throws KojiClientException {
        try (KojiClientSession session = new KojiClientSession(config.getKojiHubURL())) {
            BuildFinder finder = new BuildFinder(session, config);
            Map<BuildSystemInteger, KojiBuild> builds = finder.findBuilds(getChecksumTable());

            assertThat(builds).hasSize(2);
            assertThat(builds).hasEntrySatisfying(
                    new BuildSystemInteger(0),
                    build -> assertThat(Integer.parseInt(build.getId())).isZero());
            assertThat(builds).hasEntrySatisfying(
                    new BuildSystemInteger(966480, BuildSystem.koji),
                    build -> assertThat(build.getBuildInfo().getBuildState()).isEqualTo(KojiBuildState.DELETED));
        }
    }

    @Override
    Map<Checksum, Collection<String>> getChecksumTable() {
        Checksum checksum = new Checksum(
                ChecksumType.md5,
                "a8c05c0ff2b61c3e205fb21010581bbe",
                "infinispan-bom-9.4.16.Final-redhat-00001.pom",
                23476L);
        Collection<String> filenames = Collections.singletonList("wildfly-core-security-7.5.9.Final-redhat-2.jar");
        Map<Checksum, Collection<String>> checksumTable = new LinkedHashMap<>(2, 1.0f);

        checksumTable.put(checksum, filenames);
        return checksumTable;
    }
}
