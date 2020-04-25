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

import java.util.ArrayList;
import java.util.Collection;

import org.jboss.pnc.client.RemoteCollection;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.BuildPushResult;
import org.jboss.pnc.dto.ProductVersion;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * @author Jakub Bartecek
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class HashMapCachingClientTest {
    private HashMapCachingPncClient cachingPncClient;

    private DummyPncClient dummyPncClient;

    {
        dummyPncClient = new DummyPncClient();
        cachingPncClient = new HashMapCachingPncClient(dummyPncClient);
    }

    @Test
    public void m1ShouldGetArtifactsByMd5FromClient() throws RemoteResourceException {
        cachingPncClient.getArtifactsByMd5("md5");
        Assert.assertEquals(1, dummyPncClient.getGetArtifactsByMd5Counter());
    }

    @Test
    public void m2ShouldGetArtifactsByMd5FromCache() throws RemoteResourceException {
        cachingPncClient.getArtifactsByMd5("md5");
        Assert.assertEquals(1, dummyPncClient.getGetArtifactsByMd5Counter());
    }

    private class DummyPncClient implements PncClient {
        private Collection<Artifact> artifacts;

        private int getArtifactsByMd5Counter = 0;

        DummyPncClient() {
            artifacts = new ArrayList<>();
            artifacts.add(Artifact.builder().id("1").build());
        }

        public int getGetArtifactsByMd5Counter() {
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
