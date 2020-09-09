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

import java.util.HashMap;
import java.util.Map;

import org.jboss.pnc.build.finder.core.BuildConfig;
import org.jboss.pnc.client.RemoteCollection;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.BuildPushResult;
import org.jboss.pnc.dto.ProductVersion;

/**
 * Implementation of adapter to communicate with PNC Orchestrator REST API, which caches the results in HashMaps to
 * improve performance of the application
 *
 * @author Jakub Bartecek
 */
public class HashMapCachingPncClient implements PncClient {
    private final PncClient pncClient;

    private final Map<String, StaticRemoteCollection<Artifact>> md5Cache = new HashMap<>();

    private final Map<String, StaticRemoteCollection<Artifact>> sha1Cache = new HashMap<>();

    private final Map<String, StaticRemoteCollection<Artifact>> sha256Cache = new HashMap<>();

    private final Map<String, BuildPushResult> getBuildPushResultCache = new HashMap<>();

    private final Map<String, ProductVersion> getProductVersionCache = new HashMap<>();

    public HashMapCachingPncClient(BuildConfig config) {
        this.pncClient = new PncClientImpl(config);
    }

    public HashMapCachingPncClient(PncClient pncClient) {
        this.pncClient = pncClient;
    }

    @Override
    public RemoteCollection<Artifact> getArtifactsByMd5(String md5) throws RemoteResourceException {
        RemoteCollection<Artifact> cachedEntity = md5Cache.get(md5);
        if (cachedEntity != null) {
            return cachedEntity;
        } else {
            RemoteCollection<Artifact> foundEntity = pncClient.getArtifactsByMd5(md5);
            StaticRemoteCollection<Artifact> staticRemoteCollection = new StaticRemoteCollection<>(foundEntity);
            md5Cache.put(md5, staticRemoteCollection);
            return staticRemoteCollection;
        }
    }

    @Override
    public RemoteCollection<Artifact> getArtifactsBySha1(String sha1) throws RemoteResourceException {
        RemoteCollection<Artifact> cachedEntity = sha1Cache.get(sha1);
        if (cachedEntity != null) {
            return cachedEntity;
        } else {
            RemoteCollection<Artifact> foundEntity = pncClient.getArtifactsBySha1(sha1);
            StaticRemoteCollection<Artifact> staticRemoteCollection = new StaticRemoteCollection<>(foundEntity);
            sha1Cache.put(sha1, staticRemoteCollection);
            return staticRemoteCollection;
        }
    }

    @Override
    public RemoteCollection<Artifact> getArtifactsBySha256(String sha256) throws RemoteResourceException {
        RemoteCollection<Artifact> cachedEntity = sha256Cache.get(sha256);
        if (cachedEntity != null) {
            return cachedEntity;
        } else {
            RemoteCollection<Artifact> foundEntity = pncClient.getArtifactsBySha256(sha256);
            StaticRemoteCollection<Artifact> staticRemoteCollection = new StaticRemoteCollection<>(foundEntity);
            sha256Cache.put(sha256, staticRemoteCollection);
            return staticRemoteCollection;
        }
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
