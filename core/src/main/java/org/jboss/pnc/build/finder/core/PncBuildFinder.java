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

import static org.jboss.pnc.build.finder.core.AnsiUtils.green;
import static org.jboss.pnc.build.finder.core.BuildFinderUtils.BUILD_ID_ZERO;
import static org.jboss.pnc.build.finder.core.BuildFinderUtils.isBuildIdZero;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.pnc.EnhancedArtifact;
import org.jboss.pnc.build.finder.pnc.PncBuild;
import org.jboss.pnc.build.finder.pnc.client.PncClient;
import org.jboss.pnc.build.finder.pnc.client.PncUtils;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.client.RemoteResourceNotFoundException;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.pnc.enums.ArtifactQuality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;

/**
 * Build Finder for PNC
 *
 * @author Jakub Bartecek
 */
public class PncBuildFinder {
    private static final Logger LOGGER = LoggerFactory.getLogger(PncBuildFinder.class);

    private final long concurrentMapParallelismThreshold;

    private final PncClient pncClient;

    private final BuildFinderUtils buildFinderUtils;

    private BuildFinderListener listener;

    public PncBuildFinder(PncClient pncClient, BuildFinderUtils buildFinderUtils, BuildConfig configuration) {
        this.pncClient = pncClient;
        this.buildFinderUtils = buildFinderUtils;
        this.concurrentMapParallelismThreshold = configuration.getPncNumThreads();
    }

    public FindBuildsResult findBuildsPnc(Map<Checksum, Collection<String>> checksumTable)
            throws RemoteResourceException {
        if (checksumTable == null || checksumTable.isEmpty()) {
            LOGGER.warn("PNC Checksum table is empty");
            return new FindBuildsResult();
        }

        Set<EnhancedArtifact> artifacts = lookupArtifactsInPnc(new ConcurrentHashMap<>(checksumTable));

        ConcurrentHashMap<String, PncBuild> pncBuilds = groupArtifactsAsPncBuilds(artifacts);

        populatePncBuildsMetadata(pncBuilds);

        return convertPncBuildsToKojiBuilds(pncBuilds);
    }

    private FindBuildsResult convertPncBuildsToKojiBuilds(Map<String, PncBuild> pncBuilds) {
        FindBuildsResult findBuildsResult = new FindBuildsResult();

        pncBuilds.values().forEach(pncBuild -> {
            if (isBuildZero(pncBuild)) {
                KojiBuild kojiBuild = convertPncBuildZeroToKojiBuild(pncBuild);
                findBuildsResult.getFoundBuilds().put(new BuildSystemInteger(0, BuildSystem.none), kojiBuild);

                pncBuild.getBuiltArtifacts()
                        .forEach(
                                enhancedArtifact -> findBuildsResult.getNotFoundChecksums()
                                        .put(enhancedArtifact.getChecksum(), enhancedArtifact.getFilenames()));
            } else {
                KojiBuild kojiBuild = convertPncBuildToKojiBuild(pncBuild);
                findBuildsResult.getFoundBuilds()
                        .put(new BuildSystemInteger(kojiBuild.getId(), BuildSystem.pnc), kojiBuild);
            }
        });

        return findBuildsResult;
    }

    private void populatePncBuildsMetadata(ConcurrentHashMap<String, PncBuild> pncBuilds)
            throws RemoteResourceException {
        RemoteResourceExceptionWrapper exceptionWrapper = new RemoteResourceExceptionWrapper();

        pncBuilds.forEach(concurrentMapParallelismThreshold, (buildId, pncBuild) -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "Parallel execution of populatePncBuildsMetadata using thread {} of build {}",
                        Thread.currentThread().getName(),
                        pncBuild);
            }

            Build build = pncBuild.getBuild();

            // Skip build with id 0, which is just a container for not found artifacts
            if (!isBuildZero(pncBuild)) {
                try {
                    if (build.getProductMilestone() != null) {
                        pncBuild.setProductVersion(pncClient.getProductVersion(build.getProductMilestone().getId()));
                    }
                } catch (RemoteResourceNotFoundException e) {
                    // NOOP - keep the field empty
                } catch (RemoteResourceException e) {
                    exceptionWrapper.setException(e);
                }

                try {
                    pncBuild.setBuildPushResult(pncClient.getBuildPushResult(build.getId()));
                } catch (RemoteResourceNotFoundException e) {
                    // NOOP - keep the field empty
                } catch (RemoteResourceException e) {
                    exceptionWrapper.setException(e);
                }
            }
        });

        if (exceptionWrapper.getException() != null) {
            throw exceptionWrapper.getException();
        }
    }

    private Set<EnhancedArtifact> lookupArtifactsInPnc(ConcurrentHashMap<Checksum, Collection<String>> checksumTable)
            throws RemoteResourceException {
        Set<EnhancedArtifact> artifacts = ConcurrentHashMap.newKeySet();
        RemoteResourceExceptionWrapper exceptionWrapper = new RemoteResourceExceptionWrapper();

        checksumTable.forEach(concurrentMapParallelismThreshold, (checksum, fileNames) -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "Parallel execution of lookupArtifactsInPnc using thread {} of an artifact with checksum {}",
                        Thread.currentThread().getName(),
                        checksum);
            }

            try {
                EnhancedArtifact enhancedArtifact = new EnhancedArtifact(
                        findArtifactInPnc(checksum, fileNames).orElse(null),
                        checksum,
                        fileNames);
                artifacts.add(enhancedArtifact);

                if (listener != null && enhancedArtifact.getArtifact().isPresent()) {
                    listener.buildChecked(new BuildCheckedEvent(checksum, BuildSystem.pnc));
                }
            } catch (RemoteResourceException e) {
                exceptionWrapper.setException(e);
            }
        });

        if (exceptionWrapper.getException() != null) {
            throw exceptionWrapper.getException();
        }

        return artifacts;
    }

    /**
     * A build produces multiple artifacts. This method associates all the artifacts to the one PncBuild
     *
     * @param artifacts All found artifacts
     * @return A map pncBuildId,pncBuild
     */
    private static ConcurrentHashMap<String, PncBuild> groupArtifactsAsPncBuilds(Iterable<EnhancedArtifact> artifacts) {
        ConcurrentHashMap<String, PncBuild> pncBuilds = new ConcurrentHashMap<>();
        Build buildZero = Build.builder().id(BUILD_ID_ZERO).build();

        artifacts.forEach(artifact -> {
            Build build;

            if (artifact.getArtifact().isPresent() && artifact.getArtifact().get().getBuild() != null) {
                build = artifact.getArtifact().get().getBuild();
            } else {
                // Covers 2 cases:
                // 1) An Artifact stored in PNC DB, which was not built in PNC
                // Such artifacts are treated the same way as artifacts not found in PNC
                // 2) Artifact was not found in PNC
                // Such artifacts will be associated in a build with ID 0
                build = buildZero;
            }

            if (pncBuilds.containsKey(build.getId())) {
                pncBuilds.get(build.getId()).getBuiltArtifacts().add(artifact);
            } else {
                PncBuild pncBuild = new PncBuild(build);
                pncBuild.getBuiltArtifacts().add(artifact);
                pncBuilds.put(build.getId(), pncBuild);
            }
        });

        return pncBuilds;
    }

    /**
     * Lookups an Artifact in PNC and chooses the best candidate
     *
     * @param checksum A checksum
     * @param fileNames List of filenames
     * @return Artifact found in PNC or Optional.empty() if not found
     * @throws RemoteResourceException Thrown if a problem in communication with PNC occurs
     */
    private Optional<Artifact> findArtifactInPnc(Checksum checksum, Collection<String> fileNames)
            throws RemoteResourceException {
        if (buildFinderUtils.shouldSkipChecksum(checksum, fileNames)) {
            LOGGER.debug("Skipped checksum {} for fileNames {}", checksum, fileNames);
            return Optional.empty();
        }

        LOGGER.debug("PNC: checksum={}", checksum);

        // Lookup Artifacts and associated builds in PNC
        Collection<Artifact> artifacts = lookupPncArtifactsByChecksum(checksum);
        if (artifacts == null || artifacts.isEmpty()) {
            return Optional.empty();
        }

        return getBestPncArtifact(artifacts);
    }

    private Collection<Artifact> lookupPncArtifactsByChecksum(Checksum checksum) throws RemoteResourceException {
        switch (checksum.getType()) {
            case md5:
                return pncClient.getArtifactsByMd5(checksum.getValue()).getAll();

            case sha1:
                return pncClient.getArtifactsBySha1(checksum.getValue()).getAll();

            case sha256:
                return pncClient.getArtifactsBySha256(checksum.getValue()).getAll();

            default:
                throw new IllegalArgumentException(
                        "Unsupported checksum type requested! Checksum type " + checksum.getType()
                                + " is not supported.");
        }
    }

    private static int getArtifactQuality(Object obj) {
        Artifact a = (Artifact) obj;
        ArtifactQuality quality = a.getArtifactQuality();

        switch (quality) {
            case NEW:
                return 1;
            case VERIFIED:
                return 2;
            case TESTED:
                return 3;
            case DEPRECATED:
                return -1;
            case BLACKLISTED:
                return -2;
            case TEMPORARY:
                return -3;
            case DELETED:
                return -4;
            default:
                LOGGER.warn("Unsupported ArtifactQuality! Got: {}", quality);
                return -100;
        }
    }

    private static Optional<Artifact> getBestPncArtifact(Collection<Artifact> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            throw new IllegalArgumentException("No artifacts provided!");
        }

        if (artifacts.size() == 1) {
            return Optional.of(artifacts.iterator().next());
        } else {
            return Optional.of(
                    artifacts.stream()
                            .sorted(Comparator.comparing(PncBuildFinder::getArtifactQuality).reversed())
                            .filter(artifact -> artifact.getBuild() != null)
                            .findFirst()
                            .orElse(artifacts.iterator().next()));
        }
    }

    private KojiBuild convertPncBuildToKojiBuild(PncBuild pncBuild) {
        KojiBuild kojibuild = PncUtils.pncBuildToKojiBuild(pncBuild);

        for (EnhancedArtifact artifact : pncBuild.getBuiltArtifacts()) {
            Optional<Artifact> optionalArtifact = artifact.getArtifact();

            // XXX: Can this ever happen?
            if (optionalArtifact.isEmpty()) {
                LOGGER.warn("Enhanced artifact with checksum {} has missing artifact", artifact.getChecksum());
                continue;
            }

            KojiArchiveInfo kojiArchive = PncUtils.artifactToKojiArchiveInfo(pncBuild, optionalArtifact.get());
            PncUtils.fixNullVersion(kojibuild, kojiArchive);
            buildFinderUtils.addArchiveToBuild(kojibuild, kojiArchive, artifact.getFilenames());

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(
                        "Found build in Pnc: id: {} nvr: {} checksum: ({}) {} filenames: {}",
                        green(pncBuild.getBuild().getId()),
                        green(PncUtils.getNVRFromBuildRecord(pncBuild)),
                        green(artifact.getChecksum().getType()),
                        green(artifact.getChecksum().getValue()),
                        green(String.join(", ", artifact.getFilenames())));
            }
        }

        return kojibuild;
    }

    private KojiBuild convertPncBuildZeroToKojiBuild(PncBuild pncBuild) {
        KojiBuild buildZero = BuildFinderUtils.createKojiBuildZero();

        pncBuild.getBuiltArtifacts()
                .forEach(
                        enhancedArtifact -> buildFinderUtils.addArchiveWithoutBuild(
                                buildZero,
                                enhancedArtifact.getChecksum(),
                                enhancedArtifact.getFilenames()));

        buildFinderUtils.addFilesInError(buildZero);

        return buildZero;
    }

    /**
     * Check if PncBuild.build.id has &quot;0&quot; value, which indicates a container for not found artifacts
     *
     * @param pncBuild A PncBuild
     * @return False if the build has ID 0 otherwise true
     */
    private static boolean isBuildZero(PncBuild pncBuild) {
        return isBuildIdZero(pncBuild.getBuild().getId());
    }

    void setListener(BuildFinderListener listener) {
        this.listener = listener;
    }

    private static class RemoteResourceExceptionWrapper {
        private RemoteResourceException exception;

        RemoteResourceExceptionWrapper() {

        }

        synchronized RemoteResourceException getException() {
            return exception;
        }

        synchronized void setException(RemoteResourceException exception) {
            this.exception = exception;
        }
    }
}
