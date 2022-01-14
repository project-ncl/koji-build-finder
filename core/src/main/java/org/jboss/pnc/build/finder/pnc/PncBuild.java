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
package org.jboss.pnc.build.finder.pnc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jboss.pnc.dto.Build;
import org.jboss.pnc.dto.BuildPushResult;
import org.jboss.pnc.dto.ProductVersion;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class PncBuild {
    private Build build;

    private BuildPushResult buildPushResult;

    private ProductVersion productVersion;

    private List<EnhancedArtifact> artifacts;

    public PncBuild() {
        this.artifacts = new ArrayList<>();
    }

    public PncBuild(Build build) {
        this.build = build;
        this.artifacts = new ArrayList<>();
    }

    public PncBuild(
            Build build,
            BuildPushResult buildPushResult,
            ProductVersion productVersion,
            List<EnhancedArtifact> artifacts) {
        this.build = build;
        this.buildPushResult = buildPushResult;
        this.productVersion = productVersion;
        this.artifacts = artifacts;
    }

    @JsonIgnore
    public boolean isImport() {
        return build == null || build.getScmRepository().getInternalUrl() == null;
    }

    @JsonIgnore
    public boolean isMaven() {
        return true;
    }

    @JsonIgnore
    public Optional<String> getSource() {
        if (build != null) {
            return Optional.of(build.getScmRepository().getInternalUrl());
        }

        return Optional.empty();
    }

    public Build getBuild() {
        return build;
    }

    public void setBuild(Build build) {
        this.build = build;
    }

    public Optional<BuildPushResult> getBuildPushResult() {
        return Optional.ofNullable(buildPushResult);
    }

    public void setBuildPushResult(BuildPushResult buildPushResult) {
        this.buildPushResult = buildPushResult;
    }

    public Optional<ProductVersion> getProductVersion() {
        return Optional.ofNullable(productVersion);
    }

    public void setProductVersion(ProductVersion productVersion) {
        this.productVersion = productVersion;
    }

    public List<EnhancedArtifact> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<EnhancedArtifact> artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    public String toString() {
        return "PncBuild{" + "build=" + build + ", buildPushResult=" + buildPushResult + ", productVersion="
                + productVersion + ", artifacts=" + artifacts + '}';
    }
}
