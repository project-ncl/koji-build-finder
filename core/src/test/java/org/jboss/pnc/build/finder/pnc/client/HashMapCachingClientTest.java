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
package org.jboss.pnc.build.finder.pnc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collection;

import org.jboss.pnc.client.RemoteCollection;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.BuildPushResult;
import org.jboss.pnc.dto.ProductVersion;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * @author Jakub Bartecek
 */
@TestMethodOrder(MethodOrderer.Alphanumeric.class)
class HashMapCachingClientTest {
    private static final DummyPncClient DUMMY_PNC_CLIENT = new DummyPncClient();

    private static final PncClient HASH_MAP_CACHING_PNC_CLIENT = new HashMapCachingPncClient(DUMMY_PNC_CLIENT);

    @Test
    void m1ShouldGetArtifactsByMd5FromClient() throws RemoteResourceException {
        HASH_MAP_CACHING_PNC_CLIENT.getArtifactsByMd5("md5");
        assertEquals(1, DUMMY_PNC_CLIENT.getGetArtifactsByMd5Counter());
    }

    @Test
    void m2ShouldGetArtifactsByMd5FromCache() throws RemoteResourceException {
        HASH_MAP_CACHING_PNC_CLIENT.getArtifactsByMd5("md5");
        assertEquals(1, DUMMY_PNC_CLIENT.getGetArtifactsByMd5Counter());
    }

    private static class DummyPncClient implements PncClient {
        private final Collection<Artifact> artifacts;

        private int getArtifactsByMd5Counter;

        DummyPncClient() {
            artifacts = new ArrayList<>(1);
            artifacts.add(Artifact.builder().id("1").build());
        }

        int getGetArtifactsByMd5Counter() {
            return getArtifactsByMd5Counter;
        }

        @Override
        public RemoteCollection<Artifact> getArtifactsByMd5(String md5) {
            getArtifactsByMd5Counter++;
            return new StaticRemoteCollection<>(artifacts);
        }

        @Override
        public RemoteCollection<Artifact> getArtifactsBySha1(String sha1) {
            return null;
        }

        @Override
        public RemoteCollection<Artifact> getArtifactsBySha256(String sha256) {
            return null;
        }

        @Override
        public BuildPushResult getBuildPushResult(String buildId) {
            return null;
        }

        @Override
        public ProductVersion getProductVersion(String productMilestoneId) {
            return null;
        }
    }
}
