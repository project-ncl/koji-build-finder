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

import static org.jboss.pnc.build.finder.core.AnsiUtils.red;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiLocalArchive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildStatistics {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildStatistics.class);

    private long numberOfBuilds;

    private long numberOfArchives;

    private long numberOfImportedArchives;

    private long numberOfImportedBuilds;

    private double percentOfBuildsImported;

    private double percentOfArchivesImported;

    public BuildStatistics() {

    }

    public BuildStatistics(Collection<KojiBuild> builds) {
        for (KojiBuild build : builds) {
            boolean isImport = build.isImport();
            List<KojiLocalArchive> archives = build.getArchives();
            int archiveCount = archives.size();

            if (build.getBuildInfo().getId() > 0) {
                numberOfBuilds++;

                if (isImport) {
                    LOGGER.warn("Imported build: {}", red(build.getBuildInfo().getName()));

                    numberOfImportedBuilds++;
                }
            }

            if (archives.isEmpty()) {
                continue;
            }

            for (KojiLocalArchive archive : archives) {
                if (!isImport && !archive.isBuiltFromSource()) {
                    int unmatchedFilenamesCount = archive.getUnmatchedFilenames().size();

                    numberOfImportedArchives += unmatchedFilenamesCount;

                    LOGGER.warn(
                            "Built archive {} with {} unmatched files: {}",
                            red(archive.getArchive().getFilename()),
                            red(unmatchedFilenamesCount),
                            red(archive.getUnmatchedFilenames()));
                }
            }

            numberOfArchives += archiveCount;

            if (isImport) {
                numberOfImportedArchives += archiveCount;

                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn(
                            "Imported build {} with {} archives: {}",
                            red(build.getBuildInfo().getName()),
                            red(archiveCount),
                            red(
                                    archives.stream()
                                            .flatMap(a -> a.getFilenames().stream())
                                            .collect(Collectors.toList())));
                }
            }
        }

        if (!builds.isEmpty()) {
            percentOfBuildsImported = ((double) numberOfImportedBuilds / (double) numberOfBuilds) * 100.00;
            percentOfArchivesImported = ((double) numberOfImportedArchives / (double) numberOfArchives) * 100.00;
        }
    }

    public long getNumberOfBuilds() {
        return numberOfBuilds;
    }

    public long getNumberOfImportedBuilds() {
        return numberOfImportedBuilds;
    }

    public long getNumberOfArchives() {
        return numberOfArchives;
    }

    public long getNumberOfImportedArchives() {
        return numberOfImportedArchives;
    }

    public double getPercentOfBuildsImported() {
        return percentOfBuildsImported;
    }

    public double getPercentOfArchivesImported() {
        return percentOfArchivesImported;
    }
}
