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

import static org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY;
import static org.jboss.pnc.build.finder.core.AnsiUtils.green;
import static org.jboss.pnc.build.finder.core.AnsiUtils.red;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.jboss.pnc.build.finder.koji.ClientSession;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiLocalArchive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.koji.KojiClientException;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveType;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildState;
import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;
import com.redhat.red.build.koji.model.xmlrpc.KojiRpmInfo;

/**
 * Class providing utility operations for BuildFinder classes
 *
 * @author Jakub Bartecek
 */
public final class BuildFinderUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildFinderUtils.class);

    public static final String BUILD_ID_ZERO = "0";

    private static final List<String> NATIVE_EXTENSIONS = List.of("so", "dll", "dylib");

    private List<String> archiveExtensions;

    private final BuildConfig config;

    private final DistributionAnalyzer distributionAnalyzer;

    private final Map<ChecksumType, String> emptyDigests;

    private final Map<ChecksumType, String> emptyZipDigests;

    public BuildFinderUtils(BuildConfig config, DistributionAnalyzer distributionAnalyzer, ClientSession session) {
        this.config = config;
        this.distributionAnalyzer = distributionAnalyzer;

        loadArchiveExtensions(config, session);

        LOGGER.debug("Archive extensions: {}", green(archiveExtensions));

        emptyDigests = new EnumMap<>(ChecksumType.class);
        emptyZipDigests = new EnumMap<>(ChecksumType.class);

        config.getChecksumTypes()
                .forEach(
                        checksumType -> emptyDigests.put(
                                checksumType,
                                Hex.encodeHexString(DigestUtils.getDigest(checksumType.getAlgorithm()).digest())));

        byte[] emptyZip = emptyZipBytes();
        config.getChecksumTypes()
                .forEach(
                        checksumType -> emptyZipDigests.put(
                                checksumType,
                                Hex.encodeHexString(
                                        DigestUtils.getDigest(checksumType.getAlgorithm()).digest(emptyZip))));

    }

    private static byte[] emptyZipBytes() {
        try (ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream()) {
            try (ZipOutputStream zos = new ZipOutputStream(byteOutputStream)) {
                zos.finish();
            }
            byteOutputStream.flush();
            return byteOutputStream.toByteArray();
        } catch (IOException e) {
            LOGGER.warn("Error creating empty zip file: {}", red(e.getMessage()));
        }
        return EMPTY_BYTE_ARRAY;
    }

    public boolean shouldSkipChecksum(Checksum checksum, Collection<String> filenames) {
        if (checksum.getValue().equals(emptyDigests.get(checksum.getType()))) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Skipped empty digest for files: {}", red(String.join(", ", filenames)));
            }
            return true;
        }

        if (checksum.getValue().equals(emptyZipDigests.get(checksum.getType()))) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Skipped empty zip digest for files: {}", red(String.join(", ", filenames)));
            }
            return true;
        }

        Collection<String> newArchiveExtensions = new ArrayList<>(archiveExtensions.size() + 1);

        newArchiveExtensions.addAll(archiveExtensions);
        newArchiveExtensions.add("rpm");

        if (filenames.stream().noneMatch(filename -> newArchiveExtensions.stream().anyMatch(filename::endsWith))) {
            LOGGER.warn("Skipped due to invalid archive extension for files: {}", red(filenames));
            return false;
        }

        return false;
    }

    public void addArchiveToBuild(KojiBuild build, KojiArchiveInfo archive, Collection<String> filenames) {
        LOGGER.debug(
                "Found build id {} for file {} (checksum {}) matching local files {}",
                build.getId(),
                archive.getFilename(),
                archive.getChecksum(),
                filenames);

        Optional<KojiLocalArchive> matchingArchive = build.getArchives()
                .stream()
                .filter(a -> a.getArchive().getArchiveId().equals(archive.getArchiveId()))
                .findFirst();

        if (matchingArchive.isPresent()) {
            LOGGER.debug(
                    "Adding existing archive id {} to build id {} with {} archives and filenames {}",
                    archive.getArchiveId(),
                    archive.getBuildId(),
                    build.getArchives().size(),
                    filenames);

            KojiLocalArchive existingArchive = matchingArchive.get();

            existingArchive.getFilenames().addAll(filenames);
        } else {
            LOGGER.debug(
                    "Adding new archive id {} to build id {} with {} archives and filenames {}",
                    archive.getArchiveId(),
                    archive.getBuildId(),
                    build.getArchives().size(),
                    filenames);

            KojiLocalArchive localArchive = new KojiLocalArchive(
                    archive,
                    filenames,
                    distributionAnalyzer != null ? distributionAnalyzer.getFiles().get(filenames.iterator().next())
                            : Collections.emptySet());
            List<KojiLocalArchive> buildArchives = build.getArchives();

            buildArchives.add(localArchive);

            buildArchives.sort(Comparator.comparing(a -> a.getArchive().getFilename()));
        }
    }

    public void addArchiveWithoutBuild(KojiBuild buildZero, Checksum checksum, Collection<String> filenames) {
        addArchiveWithoutBuild(buildZero, checksum, filenames, null);
    }

    /**
     * Adds the checksum to the buildZero. If there is already a local archive associated with the checksum inside the
     * buildZero, adds the filename to the filenames associated with that archive. Since the same checksum might have
     * already been unfound (with a different checksum type), the archives are matched by any checksum associated with
     * them, not just the same checksum type.
     *
     * @param buildZero the build which is associated with all the not found archives
     * @param checksum the checksum which was not found
     * @param filenames the filenames not found
     * @param rpm the rpm associated with the checksum
     */
    public void addArchiveWithoutBuild(
            KojiBuild buildZero,
            Checksum checksum,
            Collection<String> filenames,
            KojiRpmInfo rpm) {

        Optional<KojiLocalArchive> matchingArchive = buildZero.getArchives()
                .stream()
                .filter(
                        localArchive -> localArchive.getChecksums()
                                .stream()
                                .anyMatch(
                                        cksum -> cksum.getType() == checksum.getType()
                                                && cksum.getValue().equals(checksum.getValue())))
                .findFirst();

        if (matchingArchive.isPresent()) {
            KojiLocalArchive existingArchive = matchingArchive.get();

            LOGGER.debug(
                    "Adding not-found checksum {} to existing archive id {} with filenames {}",
                    existingArchive.getArchive().getChecksum(),
                    existingArchive.getArchive().getArchiveId(),
                    filenames);

            existingArchive.getFilenames().addAll(filenames);

            if (rpm != null) {
                if (existingArchive.getRpm() != null) {
                    LOGGER.warn(
                            "Replacing RPM {} in archive {} with {}",
                            red(existingArchive.getRpm()),
                            red(existingArchive.getArchive().getArchiveId()),
                            red(rpm));
                }

                existingArchive.setRpm(rpm);
            }
        } else {
            KojiArchiveInfo tmpArchive = new KojiArchiveInfo();

            tmpArchive.setBuildId(0);
            tmpArchive.setFilename("not found");
            tmpArchive.setChecksum(checksum.getValue());
            tmpArchive.setSize((int) checksum.getFileSize());
            tmpArchive.setChecksumType(KojiChecksumType.valueOf(checksum.getType().name()));

            tmpArchive.setArchiveId(-1 * (buildZero.getArchives().size() + 1));

            LOGGER.debug(
                    "Adding not-found checksum {} to new archive id {} with filenames {} and RPM {}",
                    checksum,
                    tmpArchive.getArchiveId(),
                    filenames,
                    rpm);

            KojiLocalArchive localArchive = new KojiLocalArchive(
                    tmpArchive,
                    filenames,
                    distributionAnalyzer != null ? distributionAnalyzer.getFiles().get(filenames.iterator().next())
                            : Collections.emptySet());

            if (rpm != null) {
                localArchive.setRpm(rpm);
            }

            List<KojiLocalArchive> buildZeroArchives = buildZero.getArchives();

            buildZeroArchives.add(localArchive);

            buildZeroArchives.sort(Comparator.comparing(a -> a.getArchive().getFilename()));
        }
    }

    public static KojiBuild createKojiBuildZero() {
        KojiBuildInfo buildInfo = new KojiBuildInfo();

        buildInfo.setId(0);
        buildInfo.setPackageId(0);
        buildInfo.setBuildState(KojiBuildState.ALL);
        buildInfo.setName("not found");
        buildInfo.setVersion("not found");
        buildInfo.setRelease("not found");

        return new KojiBuild(buildInfo);
    }

    public static boolean isBuildIdZero(String id) {
        return BUILD_ID_ZERO.equals(id);
    }

    public static boolean isNotBuildIdZero(String id) {
        return !isBuildIdZero(id);
    }

    public static boolean isBuildZero(KojiBuild build) {
        return isBuildIdZero(build.getId());
    }

    public static boolean isNotBuildZero(KojiBuild build) {
        return !isBuildIdZero(build.getId());
    }

    public void addFilesInError(KojiBuild buildZero) {
        if (distributionAnalyzer != null) {
            for (FileError fileError : distributionAnalyzer.getFileErrors()) {
                String filename = fileError.getFilename();
                String extension = FilenameUtils.getExtension(filename);
                List<String> archiveExtensions = config.getArchiveExtensions();

                if (!FilenameUtils.isExtension(filename, archiveExtensions)) {
                    LOGGER.warn(
                            "Skipping file with error {} since its extension {} is not in configured list of archive extensions {}",
                            filename,
                            extension,
                            archiveExtensions);
                    continue;
                }

                Collection<Checksum> fileChecksums = distributionAnalyzer.getFiles().get(filename);

                if (fileChecksums != null) {
                    Optional<Checksum> checksum = Checksum.findByType(fileChecksums, ChecksumType.md5);
                    checksum.ifPresent(
                            cksum -> addArchiveWithoutBuild(buildZero, cksum, Collections.singletonList(filename)));
                }
            }
        }
    }

    public void loadArchiveExtensions(BuildConfig config, ClientSession session) {
        LOGGER.debug("Asking server for archive extensions");

        try {
            archiveExtensions = getArchiveExtensionsFromKoji(config, session);
        } catch (KojiClientException e) {
            LOGGER.warn("Getting archive extensions from Koji failed!", e);
            LOGGER.debug("Getting archive extensions from configuration file");
            archiveExtensions = config.getArchiveExtensions();
        }
    }

    public List<String> getArchiveExtensions() {
        return Collections.unmodifiableList(archiveExtensions);
    }

    private static List<String> getArchiveExtensionsFromKoji(BuildConfig config, ClientSession session)
            throws KojiClientException {
        Map<String, KojiArchiveType> allArchiveTypesMap = session.getArchiveTypeMap();

        List<String> allArchiveTypes = allArchiveTypesMap.values()
                .stream()
                .map(KojiArchiveType::getName)
                .collect(Collectors.toUnmodifiableList());
        List<String> archiveTypes = config.getArchiveTypes();
        List<String> archiveTypesToCheck;

        LOGGER.debug("Archive types: {}", green(archiveTypes));

        if (!archiveTypes.isEmpty()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "There are {} supplied Koji archive types: {}",
                        archiveTypes.size(),
                        String.join(", ", archiveTypes));
            }

            archiveTypesToCheck = archiveTypes.stream()
                    .filter(allArchiveTypesMap::containsKey)
                    .collect(Collectors.toUnmodifiableList());

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "There are {} valid supplied Koji archive types: {}",
                        archiveTypes.size(),
                        String.join(", ", archiveTypes));
            }
        } else {
            LOGGER.debug("There are {} known Koji archive types: {}", allArchiveTypes.size(), allArchiveTypes);
            LOGGER.warn("Supplied archive types list is empty; defaulting to all known archive types");
            archiveTypesToCheck = allArchiveTypes;
        }

        LOGGER.debug("There are {} Koji archive types to check: {}", archiveTypesToCheck.size(), archiveTypesToCheck);

        List<String> allArchiveExtensions = allArchiveTypesMap.values()
                .stream()
                .map(KojiArchiveType::getExtensions)
                .flatMap(List::stream)
                .collect(Collectors.toUnmodifiableList());
        List<String> extensions = config.getArchiveExtensions();
        List<String> extensionsToCheck;

        if (!extensions.isEmpty()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "There are {} supplied Koji archive extensions: {}",
                        extensions.size(),
                        String.join(", ", extensions));
            }

            extensionsToCheck = extensions.stream()
                    .filter(allArchiveExtensions::contains)
                    .collect(Collectors.toUnmodifiableList());

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "There are {} valid supplied Koji archive extensions: {}",
                        extensions.size(),
                        String.join(", ", extensions));
            }
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "There are {} known Koji archive extensions: {}",
                        allArchiveExtensions.size(),
                        String.join(", ", allArchiveExtensions));
            }

            LOGGER.warn("Supplied archive extensions list is empty; defaulting to all known archive extensions");
            extensionsToCheck = allArchiveExtensions;
        }

        return extensionsToCheck;
    }

    public static Map<Checksum, Collection<String>> swapEntriesWithPreferredChecksum(
            Map<Checksum, Collection<String>> originalMap,
            Map<String, Collection<Checksum>> fileInverseMap,
            ChecksumType preferredChecksumType) {

        Map<Checksum, Collection<String>> preferredChecksumMap = new HashMap<>(originalMap.size(), 1.0f);

        for (Map.Entry<Checksum, Collection<String>> entry : originalMap.entrySet()) {
            Checksum checksum = entry.getKey();
            Collection<String> files = entry.getValue();

            if (checksum.getType().equals(preferredChecksumType)) {
                // If the checksum type is already the preferred type there is no need to search further
                preferredChecksumMap.put(checksum, files);
                continue;
            }

            Collection<Checksum> fileChecksums = fileInverseMap.get(files.iterator().next());
            Optional<Checksum> preferredChecksum = fileChecksums != null
                    ? Checksum.findByType(fileChecksums, preferredChecksumType)
                    : Optional.empty();

            if (preferredChecksum.isPresent()) {
                // The preferred checksum was found, use it
                preferredChecksumMap.put(preferredChecksum.get(), files);
            } else {
                // The preferred checksum type was not found, use the original checksum
                preferredChecksumMap.put(checksum, files);
            }
        }

        return preferredChecksumMap;
    }

    public static boolean isNativeFilename(String filename) {
        return FilenameUtils.isExtension(filename, NATIVE_EXTENSIONS);
    }
}
