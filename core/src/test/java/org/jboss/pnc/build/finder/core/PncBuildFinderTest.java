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
import static org.jboss.pnc.api.constants.Attributes.BUILD_BREW_NAME;
import static org.jboss.pnc.api.constants.Attributes.BUILD_BREW_VERSION;
import static org.jboss.pnc.enums.BuildType.MVN;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.Response;

import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiClientSession;
import org.jboss.pnc.build.finder.koji.KojiLocalArchive;
import org.jboss.pnc.build.finder.pnc.client.PncClient;
import org.jboss.pnc.build.finder.pnc.client.StaticRemoteCollection;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.client.RemoteResourceNotFoundException;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.pnc.dto.BuildConfigurationRevisionRef;
import org.jboss.pnc.dto.ProjectRef;
import org.jboss.pnc.dto.SCMRepository;
import org.jboss.pnc.dto.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests of PncBuildFinder
 *
 * @author Jakub Bartecek
 */
@ExtendWith(MockitoExtension.class)
class PncBuildFinderTest {
    @Mock
    private BuildConfig buildConfig;

    @Mock
    private KojiClientSession kojiClientSession;

    @Test
    void testReturnEmptyResult() throws RemoteResourceException {
        // given
        PncClient pncClient = Mockito.mock(PncClient.class);
        BuildFinderUtils buildFinderUtils = new BuildFinderUtils(buildConfig, null, kojiClientSession);
        PncBuildFinder pncBuildFinder = new PncBuildFinder(pncClient, buildFinderUtils, buildConfig);

        // when
        FindBuildsResult findBuildsResult = pncBuildFinder.findBuildsPnc(Collections.emptyMap());

        // then
        assertThat(findBuildsResult.getFoundBuilds()).isEmpty();
        assertThat(findBuildsResult.getNotFoundChecksums()).isEmpty();
    }

    @Test
    void testFindOneBuildInPnc() throws RemoteResourceException {
        // given
        String md5 = "md5-checksum";
        LocalFile filename = new LocalFile("empty.jar", -1L);
        Checksum checksum = new Checksum(ChecksumType.md5, md5, filename);
        PncClient pncClient = Mockito.mock(PncClient.class);
        String buildId = "100";

        Map<String, String> attributes = new HashMap<>(2);
        attributes.put(BUILD_BREW_NAME, "org.empty-empty");
        attributes.put(BUILD_BREW_VERSION, "1.0.0");

        Build build = Build.builder()
                .id(buildId)
                .startTime(Instant.now())
                .submitTime(Instant.now())
                .endTime(Instant.now())
                .attributes(attributes)
                .user(User.builder().username("testUser").build())
                .scmRepository(SCMRepository.builder().internalUrl("http://repo.test/empty.git").build())
                .scmRevision("master")
                .project(ProjectRef.refBuilder().id("100").build())
                .buildConfigRevision(BuildConfigurationRevisionRef.refBuilder().id("100").buildType(MVN).build())
                .build();

        Artifact artifact = Artifact.builder()
                .id("100")
                .identifier("org.empty:empty")
                .md5(md5)
                .size(filename.getSize())
                .filename(filename.getFilename())
                .build(build)
                .build();

        when(pncClient.getArtifactsByMd5(md5)).thenReturn(createArtifactsRemoteCollection(artifact));
        when(pncClient.getBuildPushResult(buildId))
                .thenThrow(new RemoteResourceNotFoundException(new ClientErrorException(Response.Status.NOT_FOUND)));

        BuildFinderUtils buildFinderUtils = new BuildFinderUtils(buildConfig, null, kojiClientSession);
        PncBuildFinder pncBuildFinder = new PncBuildFinder(pncClient, buildFinderUtils, buildConfig);

        // when
        Map<Checksum, Collection<String>> requestMap = Collections
                .singletonMap(checksum, Collections.singletonList(filename.getFilename()));
        FindBuildsResult findBuildsResult = pncBuildFinder.findBuildsPnc(requestMap);

        // then
        assertThat(findBuildsResult.getFoundBuilds()).hasSize(1);
        assertThat(findBuildsResult.getNotFoundChecksums()).isEmpty();

        KojiBuild foundBuild = findBuildsResult.getFoundBuilds().get(new BuildSystemInteger(100, BuildSystem.pnc));
        List<KojiLocalArchive> foundArchives = foundBuild.getArchives();

        assertThat(foundArchives).hasSize(1);
        assertThat(foundArchives.get(0).getArchive().getChecksum()).isEqualTo(md5);
    }

    @Test
    void testNotFindABuildInPnc() throws RemoteResourceException {
        // given
        String givenMd5 = "md5-different";
        LocalFile filename = new LocalFile("empty.jar", -1L);
        Checksum checksum = new Checksum(ChecksumType.md5, givenMd5, filename);

        PncClient pncClient = Mockito.mock(PncClient.class);

        when(pncClient.getArtifactsByMd5(givenMd5)).thenReturn(createArtifactsRemoteCollection());

        BuildFinderUtils buildFinderUtils = new BuildFinderUtils(buildConfig, null, kojiClientSession);
        PncBuildFinder pncBuildFinder = new PncBuildFinder(pncClient, buildFinderUtils, buildConfig);

        // when
        Map<Checksum, Collection<String>> requestMap = Collections
                .singletonMap(checksum, Collections.singletonList(filename.getFilename()));
        FindBuildsResult findBuildsResult = pncBuildFinder.findBuildsPnc(requestMap);

        // then
        // Verify that only BuildZero is returned
        assertThat(findBuildsResult.getFoundBuilds()).hasSize(1);
        assertThat(findBuildsResult.getFoundBuilds()).containsOnlyKeys(new BuildSystemInteger(0));

        // Verify that the artifact is in the notFoundChecksums collection
        assertThat(findBuildsResult.getNotFoundChecksums()).hasSize(1)
                .containsEntry(checksum, Collections.singletonList(filename.getFilename()));
    }

    private static StaticRemoteCollection<Artifact> createArtifactsRemoteCollection(Artifact... artifacts) {
        return new StaticRemoteCollection<>(Collections.unmodifiableList(Arrays.asList(artifacts)));
    }
}
