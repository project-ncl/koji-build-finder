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

import java.util.Map;

import org.infinispan.commons.api.BasicCacheContainer;
import org.jboss.pnc.build.finder.core.BuildConfig;
import org.jboss.pnc.build.finder.protobuf.ArtifactStaticRemoteCollection;
import org.jboss.pnc.client.RemoteCollection;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.BuildPushResult;
import org.jboss.pnc.dto.ProductVersion;

import com.google.common.collect.Maps;

/**
 * Implementation of adapter to communicate with PNC Orchestrator REST API, which caches the results in HashMaps or ISPN
 * (if enabled) to improve performance of the application
 *
 * @author Jakub Bartecek
 */
public class CachingPncClient implements PncClient {
    private static final int ARTIFACT_CACHE_SIZE = 10844;

    private static final int GET_BUILD_PUSH_RESULT_CACHE_SIZE = 535;

    private static final int GET_PRODUC_VERSION_CACHE_SIZE = 108;

    private final PncClient pncClient;

    private final Map<String, ArtifactStaticRemoteCollection> artifactCache;

    private final Map<String, BuildPushResult> getBuildPushResultCache = Maps
            .newHashMapWithExpectedSize(GET_BUILD_PUSH_RESULT_CACHE_SIZE);

    private final Map<String, ProductVersion> getProductVersionCache = Maps
            .newHashMapWithExpectedSize(GET_PRODUC_VERSION_CACHE_SIZE);

    public CachingPncClient(BuildConfig config, BasicCacheContainer cacheManager) {
        if (cacheManager == null) {
            artifactCache = Maps.newHashMapWithExpectedSize(ARTIFACT_CACHE_SIZE);
        } else {
            artifactCache = cacheManager.getCache("artifact-pnc");
        }
        this.pncClient = new PncClientImpl(config);
    }

    public CachingPncClient(PncClient pncClient, BasicCacheContainer cacheManager) {
        if (cacheManager == null) {
            artifactCache = Maps.newHashMapWithExpectedSize(ARTIFACT_CACHE_SIZE);
        } else {
            artifactCache = cacheManager.getCache("artifact-pnc");
        }
        this.pncClient = pncClient;
    }

    @Override
    public RemoteCollection<Artifact> getArtifactsByMd5(String md5) throws RemoteResourceException {
        ArtifactStaticRemoteCollection cachedValue = getFromCache(md5);
        if (cachedValue != null) {
            return cachedValue;
        }

        RemoteCollection<Artifact> artifacts = pncClient.getArtifactsByMd5(md5);
        if (artifacts != null && artifacts.size() > 0) {
            insertToCache(md5, artifacts);
        }

        return artifacts;
    }

    @Override
    public RemoteCollection<Artifact> getArtifactsBySha1(String sha1) throws RemoteResourceException {
        ArtifactStaticRemoteCollection cachedValue = getFromCache(sha1);
        if (cachedValue != null) {
            return cachedValue;
        }

        RemoteCollection<Artifact> artifacts = pncClient.getArtifactsBySha1(sha1);
        if (artifacts != null && artifacts.size() > 0) {
            insertToCache(sha1, artifacts);
        }

        return artifacts;
    }

    @Override
    public RemoteCollection<Artifact> getArtifactsBySha256(String sha256) throws RemoteResourceException {
        ArtifactStaticRemoteCollection cachedValue = getFromCache(sha256);
        if (cachedValue != null) {
            return cachedValue;
        }

        RemoteCollection<Artifact> artifacts = pncClient.getArtifactsBySha256(sha256);
        if (artifacts != null && artifacts.size() > 0) {
            insertToCache(sha256, artifacts);
        }

        return artifacts;
    }

    private void insertToCache(String key, RemoteCollection<Artifact> value) {
        artifactCache.put(key, new ArtifactStaticRemoteCollection(value));
    }

    private ArtifactStaticRemoteCollection getFromCache(String md5) {
        if (artifactCache != null) {
            return artifactCache.get(md5);
        }
        return null;
    }

    @Override
    public BuildPushResult getBuildPushResult(String buildId) throws RemoteResourceException {
        BuildPushResult cachedEntity = getBuildPushResultCache.get(buildId);
        if (cachedEntity != null) {
            return cachedEntity;
        } else {
            BuildPushResult foundEntity = pncClient.getBuildPushResult(buildId);
            getBuildPushResultCache.put(buildId, foundEntity);
            return foundEntity;
        }
    }

    @Override
    public ProductVersion getProductVersion(String productMilestoneId) throws RemoteResourceException {
        ProductVersion cachedEntity = getProductVersionCache.get(productMilestoneId);
        if (cachedEntity != null) {
            return cachedEntity;
        } else {
            ProductVersion foundEntity = pncClient.getProductVersion(productMilestoneId);
            getProductVersionCache.put(productMilestoneId, foundEntity);
            return foundEntity;
        }
    }

    @Override
    public void close() {
        pncClient.close();
    }
}
