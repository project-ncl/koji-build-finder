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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.ClientErrorException;

import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiClientSession;
import org.jboss.pnc.build.finder.koji.KojiLocalArchive;
import org.jboss.pnc.build.finder.pnc.client.PncClient;
import org.jboss.pnc.build.finder.pnc.client.StaticRemoteCollection;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.client.RemoteResourceNotFoundException;
import org.jboss.pnc.constants.Attributes;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.pnc.dto.BuildConfigurationRevisionRef;
import org.jboss.pnc.dto.ProjectRef;
import org.jboss.pnc.dto.SCMRepository;
import org.jboss.pnc.dto.User;
import org.jboss.pnc.enums.BuildType;
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
public class PncBuildFinderTest {
    @Mock
    private BuildConfig buildConfig;

    @Mock
    private KojiClientSession kojiClientSession;

    @Test
    public void shouldReturnEmptyResult() throws RemoteResourceException {
        // given
        PncClient pncClient = Mockito.mock(PncClient.class);
        BuildFinderUtils buildFinderUtils = new BuildFinderUtils(buildConfig, null, kojiClientSession);
        PncBuildFinder pncBuildFinder = new PncBuildFinder(pncClient, buildFinderUtils);

        // when
        FindBuildsResult findBuildsResult = pncBuildFinder.findBuildsPnc(new HashMap<>());

        // then
        assertEquals(0, findBuildsResult.getFoundBuilds().size());
        assertEquals(0, findBuildsResult.getNotFoundChecksums().size());
    }

    @Test
    public void shouldFindOneBuildInPnc() throws RemoteResourceException {
        // given
        String md5 = "md5-checksum";
        String filename = "empty.jar";
        Checksum checksum = new Checksum(ChecksumType.md5, md5, filename);
        PncClient pncClient = Mockito.mock(PncClient.class);
        String buildId = "100";

        Map<String, String> attributes = new HashMap<>();
        attributes.put(Attributes.BUILD_BREW_NAME, "org.empty-empty");
        attributes.put(Attributes.BUILD_BREW_VERSION, "1.0.0");

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
                .buildConfigRevision(
                        BuildConfigurationRevisionRef.refBuilder().id("100").buildType(BuildType.MVN).build())
                .build();

        Artifact artifact = Artifact.builder()
                .id("100")
                .identifier("org.empty:empty")
                .md5(md5)
                .size(10L)
                .filename(filename)
                .build(build)
                .build();

        when(pncClient.getArtifactsByMd5(md5)).thenReturn(createArtifactsRemoteCollection(artifact));
        when(pncClient.getBuildPushResult(buildId))
                .thenThrow(new RemoteResourceNotFoundException(new ClientErrorException(404)));

        BuildFinderUtils buildFinderUtils = new BuildFinderUtils(buildConfig, null, kojiClientSession);
        PncBuildFinder pncBuildFinder = new PncBuildFinder(pncClient, buildFinderUtils);

        // when
        Map<Checksum, Collection<String>> requestMap = Collections
                .singletonMap(checksum, Collections.singletonList(filename));
        FindBuildsResult findBuildsResult = pncBuildFinder.findBuildsPnc(requestMap);

        // then
        assertEquals(1, findBuildsResult.getFoundBuilds().size());
        assertEquals(0, findBuildsResult.getNotFoundChecksums().size());

        KojiBuild foundBuild = findBuildsResult.getFoundBuilds().get(new BuildSystemInteger(100, BuildSystem.pnc));
        List<KojiLocalArchive> foundArchives = foundBuild.getArchives();

        assertEquals(1, foundArchives.size());
        assertEquals(md5, foundArchives.get(0).getArchive().getChecksum());
    }

    @Test
    public void shouldNotFindABuildInPnc() throws RemoteResourceException {
        // given
        String givenMd5 = "md5-different";
        String filename = "empty.jar";
        Checksum checksum = new Checksum(ChecksumType.md5, givenMd5, filename);

        PncClient pncClient = Mockito.mock(PncClient.class);

        when(pncClient.getArtifactsByMd5(givenMd5)).thenReturn(createArtifactsRemoteCollection());

        BuildFinderUtils buildFinderUtils = new BuildFinderUtils(buildConfig, null, kojiClientSession);
        PncBuildFinder pncBuildFinder = new PncBuildFinder(pncClient, buildFinderUtils);

        // when
        Map<Checksum, Collection<String>> requestMap = Collections
                .singletonMap(checksum, Collections.singletonList(filename));
        FindBuildsResult findBuildsResult = pncBuildFinder.findBuildsPnc(requestMap);

        // then
        // Verify that only BuildZero is returned
        assertEquals(1, findBuildsResult.getFoundBuilds().size());
        assertEquals(Integer.valueOf(0), findBuildsResult.getFoundBuilds().keySet().iterator().next().getValue());

        // Verify that the artifact is in the notFoundChecksums collection
        assertEquals(1, findBuildsResult.getNotFoundChecksums().size());
        assertTrue(findBuildsResult.getNotFoundChecksums().containsKey(checksum));
    }

    private StaticRemoteCollection<Artifact> createArtifactsRemoteCollection(Artifact... artifacts) {
        List<Artifact> artifactList = new ArrayList<>();

        for (Artifact artifact : artifacts) {
            artifactList.add(artifact);
        }

        return new StaticRemoteCollection<>(artifactList);
    }
}
