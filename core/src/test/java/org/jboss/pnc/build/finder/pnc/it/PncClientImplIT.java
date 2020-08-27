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
package org.jboss.pnc.build.finder.pnc.it;

import static com.googlecode.catchexception.CatchException.catchException;
import static com.googlecode.catchexception.CatchException.caughtException;
import static com.googlecode.catchexception.apis.CatchExceptionHamcrestMatchers.hasMessage;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.iterableWithSize;

import org.jboss.pnc.build.finder.core.it.AbstractKojiIT;
import org.jboss.pnc.client.RemoteCollection;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.client.RemoteResourceNotFoundException;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.ProductVersion;
import org.junit.jupiter.api.Test;

/**
 * @author Jakub Bartecek
 */
class PncClientImplIT extends AbstractKojiIT {
    @Test
    void shouldGetArtifactByMd5() throws RemoteResourceException {
        // given
        String checksum = "7538cd62a04a378d4c1944e26c793164";
        String artifactIdentifier = "org.apache.maven:maven-core:jar:2.2.1";

        // when
        RemoteCollection<Artifact> remoteArtifacts = getPncClient().getArtifactsByMd5(checksum);

        // then
        assertThat(remoteArtifacts, is(iterableWithSize(1)));
        assertThat(remoteArtifacts.iterator().next().getIdentifier(), is(artifactIdentifier));
    }

    @Test
    void shouldGetArtifactBySha1() throws RemoteResourceException {
        // given
        String checksum = "6f488e461188496c62e161f32160b3465ce5901e";
        String artifactIdentifier = "org.apache.maven:maven-core:jar:2.2.1";

        // when
        RemoteCollection<Artifact> remoteArtifacts = getPncClient().getArtifactsBySha1(checksum);

        // then
        assertThat(remoteArtifacts, is(iterableWithSize(1)));
        assertThat(remoteArtifacts.iterator().next().getIdentifier(), is(artifactIdentifier));
    }

    @Test
    void shouldGetArtifactBySha256() throws RemoteResourceException {
        // given
        String checksum = "cfdf0057b2d2a416d48b873afe5a2bf8d848aabbba07636149fcbb622c5952d7";
        String artifactIdentifier = "org.apache.maven:maven-core:jar:2.2.1";

        // when
        RemoteCollection<Artifact> remoteArtifacts = getPncClient().getArtifactsBySha256(checksum);

        // then
        assertThat(remoteArtifacts, is(iterableWithSize(1)));
        assertThat(remoteArtifacts.iterator().next().getIdentifier(), is(artifactIdentifier));
    }

    @Test
    void shouldNotGetArtifactBySha256() throws RemoteResourceException {
        // given
        String checksum = "invalid sha 256";

        // when
        RemoteCollection<Artifact> remoteArtifacts = getPncClient().getArtifactsBySha256(checksum);

        // then
        assertThat(remoteArtifacts, is(emptyIterable()));
    }

    @Test
    void shouldNotGetBuildPushResult() {
        catchException(
                () -> {
                    // given
                    String buildId = "-100";

                    // when
                    getPncClient().getBuildPushResult(buildId);
                });

        // then
        assertThat(
                caughtException(),
                allOf(
                        isA(RemoteResourceNotFoundException.class),
                        hasMessage("javax.ws.rs.NotFoundException: HTTP 404 Not Found")));
    }

    @Test
    void shouldGetProductVersion() throws RemoteResourceException {
        // given
        String buildId = "100";

        // when
        ProductVersion productVersion = getPncClient().getProductVersion(buildId);

        // then
        assertThat(productVersion, is(notNullValue()));
        assertThat(productVersion.getProduct(), is(notNullValue()));
    }

    @Test
    void shouldNotGetProductVersion() {
        catchException(
                () -> {
                    // given
                    String buildId = "-100";

                    // when
                    getPncClient().getProductVersion(buildId);
                });

        // then
        assertThat(
                caughtException(),
                allOf(
                        isA(RemoteResourceNotFoundException.class),
                        hasMessage("javax.ws.rs.NotFoundException: HTTP 404 Not Found")));
    }
}
