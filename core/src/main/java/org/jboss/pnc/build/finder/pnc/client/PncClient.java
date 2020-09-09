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

import org.jboss.pnc.client.RemoteCollection;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.BuildPushResult;
import org.jboss.pnc.dto.ProductVersion;

/**
 * Adapter to communicate with PNC Orchestrator REST API
 *
 * @author Jakub Bartecek
 */
public interface PncClient extends AutoCloseable {
    /**
     * Get a list of artifacts with matching md5. Returns empty list if no matching artifacts
     *
     * @param md5 md5 value
     * @return list of artifacts
     *
     * @throws RemoteResourceException Thrown in case communication with PNC fails
     */
    RemoteCollection<Artifact> getArtifactsByMd5(String md5) throws RemoteResourceException;

    /**
     * Get a list of artifacts with matching sha1. Returns empty list if no matching artifacts
     *
     * @param sha1 sha1 value
     * @return list of artifacts
     *
     * @throws RemoteResourceException Thrown in case communication with PNC fails
     */
    RemoteCollection<Artifact> getArtifactsBySha1(String sha1) throws RemoteResourceException;

    /**
     * Get a list of artifacts with matching sha256. Returns empty list if no matching artifacts
     *
     * @param sha256 sha256 value
     * @return list of artifacts
     *
     * @throws RemoteResourceException Thrown in case communication with PNC fails
     */
    RemoteCollection<Artifact> getArtifactsBySha256(String sha256) throws RemoteResourceException;

    /**
     * Gets BuildPushResult with a build specified as a parameter
     *
     * @param buildId Build ID
     * @return BuildPushResult entity
     * @throws RemoteResourceException Thrown in case communication with PNC fails
     */
    BuildPushResult getBuildPushResult(String buildId) throws RemoteResourceException;

    /**
     * Lookups ProductVersion which ProductMilestone is associated with
     *
     * @param productMilestoneId ID of a ProductMilestone
     * @return ProductVersion entity
     * @throws RemoteResourceException Thrown in case communication with PNC fails
     */
    ProductVersion getProductVersion(String productMilestoneId) throws RemoteResourceException;

    @Override
    void close();
}
