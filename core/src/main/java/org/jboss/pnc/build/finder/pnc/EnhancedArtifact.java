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
import java.util.Collection;
import java.util.Optional;

import org.jboss.pnc.build.finder.core.Checksum;
import org.jboss.pnc.dto.Artifact;

/**
 * Pnc Artifact entity with additional information needed for analysis
 *
 * @author Jakub Bartecek
 */
public class EnhancedArtifact {
    private Artifact artifact;

    private Checksum checksum;

    private Collection<String> fileNames;

    public EnhancedArtifact() {
        fileNames = new ArrayList<>();
    }

    public EnhancedArtifact(Artifact artifact, Checksum checksum, Collection<String> fileNames) {
        this.artifact = artifact;
        this.checksum = checksum;
        this.fileNames = fileNames;
    }

    public Optional<Artifact> getArtifact() {
        return Optional.ofNullable(artifact);
    }

    public void setArtifact(Artifact artifact) {
        this.artifact = artifact;
    }

    public Checksum getChecksum() {
        return checksum;
    }

    public void setChecksum(Checksum checksum) {
        this.checksum = checksum;
    }

    public Collection<String> getFilenames() {
        return fileNames;
    }

    public void setFilenames(Collection<String> filenames) {
        this.fileNames = filenames;
    }
}
