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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;

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
class DeletedBuildTest {
    @Wiremock
    private final WireMockServer server = new WireMockServer(
            WireMockConfiguration.options().usingFilesUnderClasspath("deleted-build-test").dynamicPort());

    @Test
    void verifyDeletedBuild() throws KojiClientException, MalformedURLException {
        Checksum checksum = new Checksum(
                ChecksumType.md5,
                "a8c05c0ff2b61c3e205fb21010581bbe",
                "infinispan-bom-9.4.16.Final-redhat-00001.pom");
        Collection<String> filenames = Collections.singletonList("wildfly-core-security-7.5.9.Final-redhat-2.jar");
        Map<Checksum, Collection<String>> checksumTable = new LinkedHashMap<>(2);

        checksumTable.put(checksum, filenames);

        BuildConfig config = new BuildConfig();

        config.setKojiHubURL(new URL(server.baseUrl()));

        try (KojiClientSession session = new KojiClientSession(config.getKojiHubURL())) {
            BuildFinder finder = new BuildFinder(session, config);
            Map<BuildSystemInteger, KojiBuild> builds = finder.findBuilds(checksumTable);

            assertThat(builds, is(aMapWithSize(2)));
            assertThat(builds, hasEntry(is(new BuildSystemInteger(0)), hasProperty("id", is(0))));
            assertThat(
                    builds,
                    hasEntry(
                            is(new BuildSystemInteger(966480, BuildSystem.koji)),
                            hasProperty("buildInfo", hasProperty("buildState", is(KojiBuildState.DELETED)))));
        }
    }
}
