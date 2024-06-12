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

import static org.jboss.pnc.build.finder.core.AnsiUtils.boldRed;
import static org.jboss.pnc.build.finder.core.AnsiUtils.green;
import static org.jboss.pnc.build.finder.core.AnsiUtils.red;
import static org.jboss.pnc.build.finder.core.Utils.BANG_SLASH;
import static org.jboss.pnc.build.finder.core.Utils.getAllErrorMessages;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.jboss.pnc.build.finder.koji.ClientSession;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiLocalArchive;
import org.jboss.pnc.build.finder.pnc.client.PncClient;
import org.jboss.pnc.build.finder.protobuf.ListKojiArchiveInfoProtobufWrapper;
import org.jboss.pnc.client.RemoteResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.koji.KojiClientException;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveQuery;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildState;
import com.redhat.red.build.koji.model.xmlrpc.KojiIdOrName;
import com.redhat.red.build.koji.model.xmlrpc.KojiNVRA;
import com.redhat.red.build.koji.model.xmlrpc.KojiRpmInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTagInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskInfo;

public class BuildFinder
        implements Callable<Map<BuildSystemInteger, KojiBuild>>, Supplier<Map<BuildSystemInteger, KojiBuild>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildFinder.class);

    private static final String BUILDS_FILENAME = "builds.json";

    private static final String CHECKSUMS_FILENAME_BASENAME = "checksums-";

    private final ClientSession session;

    private final BuildConfig config;

    private Map<BuildSystemInteger, KojiBuild> builds;

    private List<KojiBuild> buildsList;

    private List<KojiBuild> buildsFoundList;

    private final Map<Integer, KojiBuild> allKojiBuilds;

    private File outputDirectory;

    private final DistributionAnalyzer analyzer;

    private Map<ChecksumType, BasicCache<String, ListKojiArchiveInfoProtobufWrapper>> checksumCaches;

    private BasicCache<Integer, KojiBuild> buildCache;

    private Map<ChecksumType, BasicCache<String, KojiBuild>> rpmCaches;

    private final BasicCacheContainer cacheManager;

    private final PncBuildFinder pncBuildFinder;

    private final Map<Checksum, Collection<String>> foundChecksums;

    private final Map<Checksum, Collection<String>> notFoundChecksums;

    private final BuildFinderUtils buildFinderUtils;

    private BuildFinderListener listener;

    public BuildFinder(ClientSession session, BuildConfig config) {
        this(session, config, null, null, null);
    }

    public BuildFinder(ClientSession session, BuildConfig config, DistributionAnalyzer analyzer) {
        this(session, config, analyzer, null, null);
    }

    public BuildFinder(
            ClientSession session,
            BuildConfig config,
            DistributionAnalyzer analyzer,
            BasicCacheContainer cacheManager) {
        this(session, config, analyzer, cacheManager, null);
    }

    public BuildFinder(
            ClientSession session,
            BuildConfig config,
            DistributionAnalyzer analyzer,
            BasicCacheContainer cacheManager,
            PncClient pncclient) {
        this.session = session;
        this.config = config;
        this.outputDirectory = new File("");
        this.analyzer = analyzer;
        this.cacheManager = cacheManager;
        this.allKojiBuilds = new HashMap<>();

        this.buildFinderUtils = new BuildFinderUtils(config, analyzer, session);
        this.pncBuildFinder = new PncBuildFinder(pncclient, buildFinderUtils, config);

        if (cacheManager != null) {
            this.buildCache = cacheManager.getCache("builds");
            this.checksumCaches = new EnumMap<>(ChecksumType.class);
            this.rpmCaches = new EnumMap<>(ChecksumType.class);

            Set<ChecksumType> checksumTypes = config.getChecksumTypes();

            for (ChecksumType checksumType : checksumTypes) {
                this.checksumCaches
                        .put(checksumType, cacheManager.getCache(CHECKSUMS_FILENAME_BASENAME + checksumType));
                this.rpmCaches.put(checksumType, cacheManager.getCache("rpms-" + checksumType));
            }
        }

        this.foundChecksums = new HashMap<>();
        this.notFoundChecksums = new HashMap<>();

        initBuilds();
    }

    public static String getChecksumFilename(ChecksumType checksumType) {
        return CHECKSUMS_FILENAME_BASENAME + checksumType + ".json";
    }

    public static String getBuildsFilename() {
        return BUILDS_FILENAME;
    }

    private void initBuilds() {
        builds = new HashMap<>();
        KojiBuild build = BuildFinderUtils.createKojiBuildZero();
        builds.put(new BuildSystemInteger(0), build);
    }

    private void addArchiveWithoutBuild(Checksum checksum, Collection<String> filenames) {
        KojiBuild buildZero = builds.get(new BuildSystemInteger(0, BuildSystem.none));
        buildFinderUtils.addArchiveWithoutBuild(buildZero, checksum, filenames);
    }

    private void addRpmWithoutBuild(Checksum checksum, Collection<String> filenames, KojiRpmInfo rpm) {
        KojiBuild buildZero = builds.get(new BuildSystemInteger(0, BuildSystem.none));
        buildFinderUtils.addArchiveWithoutBuild(buildZero, checksum, filenames, rpm);
    }

    private void addArchiveToBuild(KojiBuild build, KojiArchiveInfo archive, Collection<String> filenames) {
        buildFinderUtils.addArchiveToBuild(build, archive, filenames);
    }

    private void addRpmToBuild(KojiBuild build, KojiRpmInfo rpm, Collection<String> filenames) {
        LOGGER.debug(
                "Found build id {} for RPM file {}-{}-{} (payloadhash {}) matching local files {}",
                build.getBuildInfo().getId(),
                rpm.getName(),
                rpm.getVersion(),
                rpm.getRelease(),
                rpm.getPayloadhash(),
                filenames);

        Optional<KojiLocalArchive> matchingArchive = build.getArchives()
                .stream()
                .filter(a -> a.getRpm().getId().equals(rpm.getId()))
                .findFirst();

        if (matchingArchive.isPresent()) {
            LOGGER.debug(
                    "Adding existing RPM id {} to build id {} with filenames {}",
                    rpm.getId(),
                    rpm.getBuildId(),
                    filenames);

            KojiLocalArchive existingArchive = matchingArchive.get();

            existingArchive.getFilenames().addAll(filenames);
        } else {
            LOGGER.debug(
                    "Adding new rpm id {} to build id {} with filenames {}",
                    rpm.getId(),
                    rpm.getBuildId(),
                    filenames);

            List<KojiLocalArchive> buildArchives = build.getArchives();

            buildArchives.add(
                    new KojiLocalArchive(
                            rpm,
                            filenames,
                            analyzer != null ? analyzer.getFiles().get(filenames.iterator().next())
                                    : Collections.emptySet()));

            buildArchives.sort(Comparator.comparing(a -> a.getArchive().getFilename()));
        }
    }

    private void handleRPMs(Collection<Entry<Checksum, Collection<String>>> rpmEntries, ExecutorService pool)
            throws KojiClientException, ExecutionException, InterruptedException {
        List<KojiIdOrName> rpmBuildIdsOrNames = new ArrayList<>(rpmEntries.size());

        for (Entry<Checksum, Collection<String>> rpmEntry : rpmEntries) {
            Collection<String> filenames = rpmEntry.getValue();

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("RPM entry has filenames: {}", green(String.join(", ", filenames)));
            }

            Optional<String> rpmFilename = filenames.stream().filter(filename -> filename.endsWith(".rpm")).findFirst();

            if (rpmFilename.isPresent()) {
                // Let's make sure we have the filename only, removing the parent path (if any)
                String name = rpmFilename.map(Paths::get).map(Path::getFileName).map(Path::toString).orElse("");
                KojiNVRA nvra = KojiNVRA.parseNVRA(name);
                KojiIdOrName idOrName = KojiIdOrName.getFor(
                        nvra.getName() + "-" + nvra.getVersion() + "-" + nvra.getRelease() + "." + nvra.getArch());

                rpmBuildIdsOrNames.add(idOrName);

                LOGGER.debug("Added RPM: {}", rpmBuildIdsOrNames.get(rpmBuildIdsOrNames.size() - 1));
            }
        }

        Future<List<KojiRpmInfo>> futureRpmInfos = pool.submit(() -> session.getRPM(rpmBuildIdsOrNames));
        List<KojiRpmInfo> rpmInfos = futureRpmInfos.get();
        // XXX: We can't use sorted()/distinct() here because it will cause the lists to not match up with the RPM
        // entries
        List<KojiIdOrName> rpmBuildIds = rpmInfos.stream()
                .filter(Objects::nonNull)
                .map(KojiRpmInfo::getBuildId)
                .filter(Objects::nonNull)
                .map(KojiIdOrName::getFor)
                .collect(Collectors.toUnmodifiableList());
        Future<List<KojiBuildInfo>> futureRpmBuildInfos = pool.submit(() -> session.getBuild(rpmBuildIds));
        Future<List<List<KojiTagInfo>>> futureRpmTagInfos = pool.submit(() -> session.listTags(rpmBuildIds));
        Future<List<List<KojiRpmInfo>>> futureRpmRpmInfos = pool.submit(() -> session.listBuildRPMs(rpmBuildIds));
        List<KojiBuildInfo> rpmBuildInfos = futureRpmBuildInfos.get();
        List<Integer> taskIds = rpmBuildInfos.stream()
                .map(KojiBuildInfo::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());
        int taskIdsSize = taskIds.size();
        List<KojiTaskInfo> rpmTaskInfos = null;
        Future<List<KojiTaskInfo>> futureRpmTaskInfos = null;

        if (taskIdsSize > 0) {
            Boolean[] a = new Boolean[taskIdsSize];
            Arrays.fill(a, Boolean.TRUE);
            List<Boolean> requests = List.of(a);
            futureRpmTaskInfos = pool.submit(() -> session.getTaskInfo(taskIds, requests));
        } else {
            rpmTaskInfos = Collections.emptyList();
        }

        Iterator<Entry<Checksum, Collection<String>>> it = rpmEntries.iterator();
        Iterator<KojiRpmInfo> itrpm = rpmInfos.iterator();
        Iterator<KojiBuildInfo> itbuilds = rpmBuildInfos.iterator();
        List<List<KojiTagInfo>> rpmTagInfos = futureRpmTagInfos.get();
        Iterator<List<KojiTagInfo>> ittags = rpmTagInfos.iterator();
        List<List<KojiRpmInfo>> rpmRpmInfos = futureRpmRpmInfos.get();
        Iterator<List<KojiRpmInfo>> itrpms = rpmRpmInfos.iterator();

        if (futureRpmTaskInfos != null) {
            rpmTaskInfos = futureRpmTaskInfos.get();
        }

        Iterator<KojiTaskInfo> ittasks = rpmTaskInfos.iterator();

        while (it.hasNext()) {
            Entry<Checksum, Collection<String>> entry = it.next();
            Checksum checksum = entry.getKey();
            Collection<String> filenames = entry.getValue();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("After processing, RPM entry has filenames: {}", String.join(", ", filenames));
            }

            KojiRpmInfo rpm = itrpm.next();

            LOGGER.debug(
                    "Processing checksum: {}, filenames: {}, rpm: {}",
                    green(checksum),
                    green(filenames),
                    green(rpm));

            if (rpm == null) {
                LOGGER.debug("Got null RPM for checksum: {}, filenames: {}", checksum, String.join(", ", filenames));
                markNotFound(entry);
                continue;
            } else if (rpm.getBuildId() == null) {
                LOGGER.warn(
                        "Skipped build lookup for RPM {} with {} checksum {}, since it did not have an associated build id",
                        red(rpm.getNvr()),
                        red(checksum.getType()),
                        red(checksum.getValue()));

                if (rpm.getExternalRepoId() != null) {
                    LOGGER.warn(
                            "RPM {} was imported from external repository {}:{}",
                            red(rpm.getNvr()),
                            red(rpm.getExternalRepoId()),
                            red(rpm.getExternalRepoName()));
                }

                markFound(entry);
                addRpmWithoutBuild(checksum, filenames, rpm);

                continue;
            }

            // XXX: Only works for md5, and we can't look up RPMs by checksum
            // XXX: We can use other APIs to get other checksums, but they are not cached as part of this object
            if (checksum.getType() == ChecksumType.md5) {
                String actual = rpm.getPayloadhash();

                if (!checksum.getValue().equals(actual)) {
                    throw new KojiClientException("Mismatched payload hash: " + checksum + " != " + actual);
                }
            }

            KojiBuild build = new KojiBuild();

            build.setBuildInfo(itbuilds.next());
            build.setTags(ittags.next());

            if (build.getBuildInfo().getTaskId() != null) {
                build.setTaskInfo(ittasks.next());
            }

            build.setRemoteRpms(itrpms.next());

            addRpmToBuild(build, rpm, filenames);

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(
                        "Found build in Koji: id: {} nvr: {} checksum: ({}) {} filenames: {} RPM: {}-{}-{}.{}.rpm",
                        green(build.getBuildInfo().getId()),
                        green(build.getBuildInfo().getNvr()),
                        green(checksum.getType()),
                        green(checksum.getValue()),
                        green(String.join(", ", filenames)),
                        green(rpm.getName()),
                        green(rpm.getVersion()),
                        green(rpm.getRelease()),
                        green(rpm.getArch()));
            }

            markFound(entry);

            Integer id = build.getBuildInfo().getId();

            allKojiBuilds.put(id, build);

            if (cacheManager != null) {
                KojiBuild cachedBuild = buildCache.put(id, build);

                if (cachedBuild != null && !cachedBuild.getBuildInfo().getTypeNames().contains("rpm")) {
                    LOGGER.warn("Build id {} was already cached, but this should never happen", red(id));
                }
            }

            builds.put(new BuildSystemInteger(id, BuildSystem.koji), build);
        }
    }

    /**
     * Given a list of builds sorted by id, return the best build chosen in the following order:
     *
     * <ol>
     * <li>Complete tagged non-imported builds</li>
     * <li>Complete tagged imported builds</li>
     * <li>Complete untagged builds</li>
     * <li>Builds with the highest id</li>
     * </ol>
     *
     * @param candidates the list of builds in order of increasing id
     * @param archives the archives which are contained in the list of found builds
     * @return the best build
     */
    private KojiBuild findBestBuildFromCandidates(List<KojiBuild> candidates, List<KojiArchiveInfo> archives) {
        int candidatesSize = candidates.size();

        if (candidatesSize == 1) {
            return candidates.get(0);
        }

        String checksum = archives.get(0).getChecksum();
        List<Integer> candidateIds = candidates.stream()
                .map(KojiBuild::getBuildInfo)
                .map(KojiBuildInfo::getId)
                .collect(Collectors.toUnmodifiableList());

        LOGGER.debug("Found {} builds containing archive with checksum {}: {}", candidatesSize, checksum, candidateIds);

        for (KojiArchiveInfo archive : archives) {
            KojiBuild duplicateBuild = builds.get(new BuildSystemInteger(archive.getBuildId(), BuildSystem.koji));

            if (duplicateBuild != null) {
                LOGGER.debug(
                        "Marking archive id {} as duplicate for build id {}",
                        archive.getArchiveId(),
                        duplicateBuild.getBuildInfo().getId());

                if (!duplicateBuild.getDuplicateArchives().contains(archive)) {
                    duplicateBuild.getDuplicateArchives().add(archive);
                }
            }
        }

        List<KojiBuild> cachedBuilds = candidateIds.stream()
                .map(id -> builds.get(new BuildSystemInteger(id, BuildSystem.koji)))
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());

        if (!cachedBuilds.isEmpty()) {
            KojiBuild build = cachedBuilds.get(cachedBuilds.size() - 1);

            LOGGER.debug("Found suitable cached build id {}", build.getBuildInfo().getId());

            return build;
        }

        List<KojiBuild> completedBuilds = candidates.stream()
                .filter(build -> build.getBuildInfo().getBuildState() == KojiBuildState.COMPLETE)
                .collect(Collectors.toUnmodifiableList());
        List<KojiBuild> completedTaggedBuilds = completedBuilds.stream()
                .filter(build -> build.getTags() != null && !build.getTags().isEmpty())
                .collect(Collectors.toUnmodifiableList());
        List<KojiBuild> completedTaggedBuiltBuilds = completedTaggedBuilds.stream()
                .filter(build -> !build.isImport())
                .collect(Collectors.toUnmodifiableList());

        if (!completedTaggedBuiltBuilds.isEmpty()) {
            KojiBuild build = completedTaggedBuiltBuilds.get(completedTaggedBuiltBuilds.size() - 1);

            LOGGER.debug(
                    "Found suitable completed non-import tagged build {} for checksum {}",
                    build.getBuildInfo().getId(),
                    checksum);

            return build;
        }

        if (!completedTaggedBuilds.isEmpty()) {
            KojiBuild build = completedTaggedBuilds.get(completedTaggedBuilds.size() - 1);

            LOGGER.debug(
                    "Found suitable completed tagged build {} for checksum {}",
                    build.getBuildInfo().getId(),
                    checksum);

            return build;
        }

        if (!completedBuilds.isEmpty()) {
            KojiBuild build = completedBuilds.get(completedBuilds.size() - 1);

            LOGGER.debug("Found suitable completed build {} for checksum {}", build.getBuildInfo().getId(), checksum);

            return build;
        }

        KojiBuild build = candidates.get(candidatesSize - 1);

        LOGGER.warn(
                "Could not find suitable build for checksum {} for build id {}. Keeping latest",
                red(checksum),
                red(build.getBuildInfo().getId()));

        return build;
    }

    /**
     * This method takes as input a filename which could not be found in the analysis (for example
     * "foo.tar!/foo/bar.zip!/bar/jansi-1.18.0.redhat-00001.jar"). It will iterate over all the builds local archives
     * and search the ones that have a file name matching the parent of the filename specified (in this example
     * "foo.tar!/foo/bar.zip"). If a match is found it will add the provided filename to the list of the unmatched
     * filenames of the local archive. This method will iterate recursively on all the parents of the filename (parents
     * are separated by "!/") until a local archive is matched.
     *
     * @param filename the filename
     * @return the parent of the file name (separated by "!/"")
     */
    private Optional<String> handleNotFoundFile(String filename) {
        LOGGER.debug("Handle not found file: {}", filename);

        int index = filename.lastIndexOf(BANG_SLASH);

        if (index == -1) {
            index = filename.length();
        }

        String parentFilename = filename.substring(0, index);

        LOGGER.debug("Parent of not found file: {}", parentFilename);

        for (KojiBuild build : builds.values()) {
            List<KojiLocalArchive> as = build.getArchives();
            Optional<KojiLocalArchive> a = as.stream()
                    .filter(ar -> ar.getFilenames().contains(parentFilename))
                    .findFirst();

            if (a.isPresent()) {
                KojiLocalArchive matchedArchive = a.get();
                KojiArchiveInfo archive = matchedArchive.getArchive();
                matchedArchive.getUnmatchedFilenames().add(filename);

                LOGGER.debug(
                        "Archive {} ({}) contains not found file {} (built from source: {})",
                        archive.getArchiveId(),
                        archive.getFilename(),
                        filename,
                        matchedArchive.isBuiltFromSource());

                return Optional.of(parentFilename);
            }
        }

        if (index == filename.length()) {
            return Optional.empty();
        }

        return handleNotFoundFile(parentFilename);
    }

    /**
     * This method accepts the name of a file which was found in the analysis. It will search all the builds local
     * archives, whose name matches the parent of the filename specified, and removes the provided filename from the
     * list of the unmatched filenames associated with the local archive. This in case a filename could not be found
     * using a particular checksum type, but then was found later using another checksum type.
     *
     * @param filename the filename
     * @return the parent of the file name (separated by "!/"")
     */
    private Optional<String> handleFoundFile(String filename) {
        LOGGER.debug("Handle found file: {}", filename);

        int index = filename.lastIndexOf(BANG_SLASH);

        if (index == -1) {
            index = filename.length();
        }

        String parentFilename = filename.substring(0, index);

        LOGGER.debug("Parent of found file: {}", parentFilename);

        for (KojiBuild build : builds.values()) {
            List<KojiLocalArchive> as = build.getArchives();
            Optional<KojiLocalArchive> a = as.stream()
                    .filter(ar -> ar.getFilenames().contains(parentFilename))
                    .findFirst();

            if (a.isPresent()) {
                KojiLocalArchive matchedArchive = a.get();
                KojiArchiveInfo archive = matchedArchive.getArchive();
                matchedArchive.getUnmatchedFilenames().remove(filename);

                LOGGER.debug(
                        "Archive {} ({}) had not found file removed {} (built from source: {})",
                        archive.getArchiveId(),
                        archive.getFilename(),
                        filename,
                        matchedArchive.isBuiltFromSource());

                return Optional.of(parentFilename);
            }
        }

        if (index == filename.length()) {
            return Optional.empty();
        }

        return handleFoundFile(parentFilename);
    }

    /**
     * Find builds with the given checksums.
     *
     * @param checksumTable the checksum table
     * @return the map of builds
     * @throws KojiClientException if an error occurs
     */
    public Map<BuildSystemInteger, KojiBuild> findBuilds(Map<Checksum, Collection<String>> checksumTable)
            throws KojiClientException {
        if (checksumTable == null || checksumTable.isEmpty()) {
            LOGGER.warn("Koji Checksum table is empty");
            return Collections.emptyMap();
        }

        Set<Entry<Checksum, Collection<String>>> entries = checksumTable.entrySet();
        int numEntries = entries.size();
        List<Entry<Checksum, Collection<String>>> checksums = new ArrayList<>(numEntries);
        Collection<Entry<Checksum, Collection<String>>> cachedChecksums = new ArrayList<>(numEntries);
        Collection<List<KojiArchiveInfo>> cachedArchiveInfos = new ArrayList<>(numEntries);
        Collection<Entry<Checksum, Collection<String>>> rpmEntries = new ArrayList<>(numEntries);

        /*
         * Determine whether the checksums to be found have been previously cached
         */
        for (Entry<Checksum, Collection<String>> entry : entries) {
            Checksum checksum = entry.getKey();
            Collection<String> filenames = entry.getValue();

            if (buildFinderUtils.shouldSkipChecksum(checksum, filenames)) {
                LOGGER.debug("Skipped checksum {} for filenames {}", checksum, filenames);
                // FIXME: We must check for a cached copy and remove it if present
                continue;
            }

            KojiBuild cacheRpmBuildInfo;

            List<KojiArchiveInfo> cacheArchiveInfos;

            if (filenames.stream().anyMatch(filename -> filename.endsWith(".rpm"))) {
                if (cacheManager == null
                        || (cacheRpmBuildInfo = rpmCaches.get(ChecksumType.md5).get(checksum.getValue())) == null) {
                    LOGGER.debug("Add RPM entry {} to list", entry);
                    rpmEntries.add(entry);
                } else {
                    LOGGER.debug("Checksum {} cached with build id {}", green(checksum), green(cacheRpmBuildInfo));
                    rpmCaches.get(checksum.getType()).put(checksum.getValue(), cacheRpmBuildInfo);
                    buildCache.put(cacheRpmBuildInfo.getBuildInfo().getId(), cacheRpmBuildInfo);
                }
            } else {
                ListKojiArchiveInfoProtobufWrapper wrapper = null;

                if (checksumCaches != null) {
                    wrapper = checksumCaches.get(ChecksumType.md5).get(checksum.getValue());
                }

                if (cacheManager == null || wrapper == null) {
                    LOGGER.debug("Add checksum {} to list", checksum);
                    checksums.add(entry);
                } else {
                    cacheArchiveInfos = wrapper.getData();
                    LOGGER.debug(
                            "Checksum {} cached with build ids {}",
                            green(checksum),
                            green(
                                    cacheArchiveInfos.stream()
                                            .map(KojiArchiveInfo::getBuildId)
                                            .collect(Collectors.toUnmodifiableList())));
                    cachedChecksums.add(entry);
                    cachedArchiveInfos.add(cacheArchiveInfos);
                }
            }
        }

        /*
         * For any checksum which was not already in the cache, get a list of KojiArchiveInfo by submitting a list of
         * KojiArchiveQuery with a checksum value to find.
         */
        int numThreads = config.getKojiNumThreads();
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        int numChecksums = checksums.size();
        List<List<KojiArchiveInfo>> archives = new ArrayList<>(numChecksums);
        int chunkSize = config.getKojiMulticallSize();
        List<List<Entry<Checksum, Collection<String>>>> chunks = ListUtils.partition(checksums, chunkSize);
        int numChunks = chunks.size();
        Collection<KojiArchiveQuery> allQueries = new ArrayList<>(numChecksums);

        if (numChecksums > 0) {
            LOGGER.debug("Looking up {} checksums", green(numChecksums));
            LOGGER.debug("Using {} chunks of size {}", green(numChunks), green(chunkSize));

            Collection<Callable<List<List<KojiArchiveInfo>>>> tasks = new ArrayList<>(numChecksums);

            for (int i = 0; i < numChunks; i++) {
                int chunkNumber = i + 1;
                List<Entry<Checksum, Collection<String>>> chunk = chunks.get(i);
                List<KojiArchiveQuery> queries = new ArrayList<>(numChunks);

                for (Entry<Checksum, Collection<String>> entry : chunk) {
                    Checksum checksum = entry.getKey();
                    KojiArchiveQuery query = new KojiArchiveQuery().withChecksum(checksum.getValue());

                    LOGGER.debug("Adding query for checksum {}", checksum);

                    queries.add(query);
                }

                if (!queries.isEmpty()) {
                    int querySize = queries.size();

                    LOGGER.debug("Added {} queries", green(querySize));

                    allQueries.addAll(queries);

                    tasks.add(() -> {
                        LOGGER.debug("Looking up checksums for chunk {}/{}", green(chunkNumber), green(numChunks));
                        return session.listArchives(queries);
                    });
                }
            }

            try {
                List<Future<List<List<KojiArchiveInfo>>>> futures = pool.invokeAll(tasks);

                for (Future<List<List<KojiArchiveInfo>>> future : futures) {
                    try {
                        List<List<KojiArchiveInfo>> archiveFutures = future.get();
                        archives.addAll(archiveFutures);
                    } catch (ExecutionException e) {
                        Utils.shutdownAndAwaitTermination(pool);
                        LOGGER.error("Error getting Koji archives: {}", boldRed(getAllErrorMessages(e)));
                        LOGGER.debug("Error", e);
                        throw new KojiClientException("Error getting Koji archives", e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Utils.shutdownAndAwaitTermination(pool);
                LOGGER.error("Koji archive thread interrupted: {}", boldRed(getAllErrorMessages(e)));
                LOGGER.debug("Error", e);
                throw new KojiClientException("Koji archive thread interrupted", e);
            }
        }

        List<KojiArchiveInfo> archivesToEnrich = archives.stream()
                .flatMap(List::stream)
                .collect(Collectors.toUnmodifiableList());

        session.enrichArchiveTypeInfo(archivesToEnrich);

        /*
         * For any KojiArchiveInfo create a protobuf wrapper and add it to the checksum cache.
         */
        Iterator<KojiArchiveQuery> itqueries = allQueries.iterator();

        for (List<KojiArchiveInfo> archiveList : archives) {
            String queryChecksum = itqueries.next().getChecksum();

            if (archiveList.isEmpty()) {
                if (cacheManager != null) {
                    checksumCaches.get(ChecksumType.md5).put(queryChecksum, new ListKojiArchiveInfoProtobufWrapper());
                }
            } else {
                String archiveChecksum = archiveList.get(0).getChecksum();

                if (!queryChecksum.equals(archiveChecksum)) {
                    LOGGER.warn(
                            "Checksums {} and {} don't match, but this should never happen",
                            queryChecksum,
                            archiveChecksum);
                }

                if (cacheManager != null) {
                    checksumCaches.get(ChecksumType.md5)
                            .put(queryChecksum, new ListKojiArchiveInfoProtobufWrapper(archiveList));
                }
            }
        }

        /*
         * Create a list of buildIds associated with all the KojiArchiveInfo found (either already in the cache or just
         * queried)
         */
        Stream<Integer> archiveBuildIds = archives.stream().flatMap(List::stream).map(KojiArchiveInfo::getBuildId);
        Stream<Integer> cachedBuildIds = cachedArchiveInfos.stream()
                .flatMap(List::stream)
                .map(KojiArchiveInfo::getBuildId);
        List<Integer> buildIds = Stream.concat(archiveBuildIds, cachedBuildIds)
                .sorted()
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        int buildIdsSize = buildIds.size();

        /*
         * For any buildId in the list, remove the ones already present in the cache
         */
        if (cacheManager != null) {
            Iterator<Integer> it = buildIds.iterator();

            while (it.hasNext()) {
                Integer id = it.next();
                KojiBuild build = buildCache.get(id);

                if (build != null) {
                    LOGGER.debug(
                            "Build with id {} and nvr {} has been previously cached",
                            green(id),
                            green(build.getBuildInfo().getNvr()));
                    allKojiBuilds.put(id, build);
                    it.remove();
                }
            }
        }

        if (!rpmEntries.isEmpty()) {
            try {
                handleRPMs(rpmEntries, pool);
            } catch (ExecutionException e) {
                Utils.shutdownAndAwaitTermination(pool);
                throw new KojiClientException("Error handling RPMs", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Utils.shutdownAndAwaitTermination(pool);
                throw new KojiClientException("Error handling RPMs", e);
            }
        }

        /*
         * For any remaining buildId in the list (the ones not already in the cache): 1- find all the KojiBuildInfo by
         * submitting a list of KojiArchiveQuery with a buildId. 2- find all the KojiTaskInfo associated with each
         * build. 3- find all the KojiTagInfo associated with each build.
         */
        if (!buildIds.isEmpty()) {
            List<KojiIdOrName> idsOrNames = buildIds.stream()
                    .map(KojiIdOrName::getFor)
                    .collect(Collectors.toUnmodifiableList());
            Future<List<KojiBuildInfo>> futureArchiveBuilds = pool.submit(() -> session.getBuild(idsOrNames));
            Future<List<List<KojiTagInfo>>> futureTagInfos = pool.submit(() -> session.listTags(idsOrNames));
            List<KojiArchiveQuery> queries = new ArrayList<>(buildIdsSize);

            for (Integer buildId : buildIds) {
                KojiArchiveQuery query = new KojiArchiveQuery().withBuildId(buildId);
                queries.add(query);
            }

            Future<List<List<KojiArchiveInfo>>> futureArchiveInfos = pool.submit(() -> session.listArchives(queries));
            List<KojiBuildInfo> archiveBuilds;
            List<List<KojiArchiveInfo>> archiveInfos;

            try {
                archiveBuilds = futureArchiveBuilds.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Utils.shutdownAndAwaitTermination(pool);
                throw new KojiClientException("Error getting archive build futures", e);
            } catch (ExecutionException e) {
                Utils.shutdownAndAwaitTermination(pool);
                throw new KojiClientException("Error getting archive build futures", e);
            }

            List<Integer> taskIds = new ArrayList<>(archiveBuilds.size());

            for (KojiBuildInfo archiveBuild : archiveBuilds) {
                Integer taskId = archiveBuild.getTaskId();

                if (taskId != null) {
                    taskIds.add(taskId);
                }
            }
            int taskIdsSize = taskIds.size();
            Future<List<KojiTaskInfo>> futureTaskInfos = null;

            if (taskIdsSize > 0) {
                Boolean[] a = new Boolean[taskIdsSize];
                Arrays.fill(a, Boolean.TRUE);
                List<Boolean> requests = List.of(a);
                futureTaskInfos = pool.submit(() -> session.getTaskInfo(taskIds, requests));
            }

            List<List<KojiTagInfo>> tagInfos;
            List<KojiTaskInfo> taskInfos = Collections.emptyList();

            try {
                tagInfos = futureTagInfos.get();
                archiveInfos = futureArchiveInfos.get();

                if (futureTaskInfos != null) {
                    taskInfos = futureTaskInfos.get();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Utils.shutdownAndAwaitTermination(pool);
                throw new KojiClientException("Error getting tag, archive, or taskinfo futures", e);
            } catch (ExecutionException e) {
                Utils.shutdownAndAwaitTermination(pool);
                throw new KojiClientException("Error getting tag, archive, or taskinfo futures", e);
            }

            Iterator<KojiBuildInfo> itbuilds = archiveBuilds.iterator();
            Iterator<List<KojiTagInfo>> ittags = tagInfos.iterator();
            Iterator<List<KojiArchiveInfo>> itArchiveInfos = archiveInfos.iterator();
            Iterator<KojiTaskInfo> ittasks = taskInfos.iterator();

            /*
             * Link all the tags, archives, task found to the corresponding builds and add them to the cache
             */
            while (itbuilds.hasNext()) {
                KojiBuildInfo buildInfo = itbuilds.next();
                KojiBuild build = new KojiBuild(buildInfo);

                build.setTags(ittags.next());
                build.setRemoteArchives(itArchiveInfos.next());

                if (build.getBuildInfo().getTaskId() != null) {
                    build.setTaskInfo(ittasks.next());
                }

                Integer id = build.getBuildInfo().getId();

                allKojiBuilds.put(id, build);

                if (cacheManager != null) {
                    KojiBuild cachedBuild = buildCache.put(id, build);

                    if (cachedBuild != null) {
                        LOGGER.warn("Build id {} was already cached, but this should never happen", red(id));
                    }
                }
            }

            /*
             * Find the optional scmSourceZip, projectSourceZip and patchesZip and them to each archive
             */
            List<KojiArchiveInfo> archivesToUpdate = new ArrayList<>(3 * archiveBuilds.size());

            Collection<KojiBuild> values = allKojiBuilds.values();

            for (KojiBuild build : values) {
                List<Optional<KojiArchiveInfo>> sources = Arrays
                        .asList(build.getScmSourcesZip(), build.getProjectSourcesTgz(), build.getPatchesZip());

                for (Optional<KojiArchiveInfo> optional : sources) {
                    if (optional.isPresent()) {
                        KojiArchiveInfo source = optional.get();

                        if (KojiLocalArchive.isMissingBuildTypeInfo(source)) {
                            archivesToUpdate.add(source);
                        }
                    }
                }
            }

            if (!archivesToUpdate.isEmpty()) {
                session.enrichArchiveTypeInfo(archivesToUpdate);
            }
        }

        checksums.addAll(cachedChecksums);
        archives.addAll(cachedArchiveInfos);

        LOGGER.debug("Add builds with {} checksums and {} archive lists", checksums.size(), archives.size());

        Iterator<Entry<Checksum, Collection<String>>> itchecksums = checksums.iterator();
        Iterator<List<KojiArchiveInfo>> itarchives = archives.iterator();

        /*
         * For every checksum, if a KojiArchiveInfo is not found, add the checksum to the notFoundChecksums map
         * (<code>markNotFound</code>>). The `markNotFound` method will also add the not found checksum to the
         * buildZero. <p/> Otherwise, if one KojiArchiveInfo is found for that checksum (in case more than one is found,
         * select the best match), add it to the <code>builds</code> map, and add the checksum to the foundChecksums
         * (markFound). The <code>markFound</code> method will also remove the checksum from the notFoundChecksums map,
         * remove the archives matching the checksum from the buildZero, and remove all the filenames associated with
         * the checksum from the unmatched filenames.
         *
         */
        while (itchecksums.hasNext()) {
            Entry<Checksum, Collection<String>> entry = itchecksums.next();
            Checksum checksum = entry.getKey();
            Collection<String> filenames = entry.getValue();
            List<KojiArchiveInfo> localArchiveInfos = itarchives.next();
            int size = localArchiveInfos.size();

            if (size == 0) {
                LOGGER.debug("Got empty archive list for checksum: {}", green(checksum));
                markNotFound(entry);
            } else {

                KojiArchiveInfo archive;
                Integer buildId;
                String archiveFilenames;

                if (size == 1) {
                    archive = localArchiveInfos.get(0);
                    buildId = archive.getBuildId();
                    archiveFilenames = archive.getFilename();

                    LOGGER.debug("Singular build id {} found for checksum {}", green(buildId), green(checksum));
                } else {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(
                                "Find best build for checksum {} and filenames {} out of {} archives: {}",
                                green(checksum),
                                green(filenames),
                                green(size),
                                localArchiveInfos.stream()
                                        .map(KojiArchiveInfo::getBuildId)
                                        .map(String::valueOf)
                                        .collect(Collectors.joining(", ")));
                    }

                    KojiBuild bestBuild = findBestBuild(allKojiBuilds, localArchiveInfos);
                    Optional<KojiArchiveInfo> optionalArchive = localArchiveInfos.stream()
                            .filter(a -> a.getBuildId().equals(bestBuild.getBuildInfo().getId()))
                            .findFirst();

                    if (optionalArchive.isPresent()) {
                        archive = optionalArchive.get();
                        buildId = archive.getBuildId();
                        archiveFilenames = localArchiveInfos.stream()
                                .filter(a -> a.getBuildId().equals(buildId))
                                .map(KojiArchiveInfo::getFilename)
                                .collect(Collectors.joining(", "));

                        LOGGER.debug(
                                "Build id {} found for checksum {}",
                                green(bestBuild.getBuildInfo().getId()),
                                green(checksum));
                    } else {
                        continue;
                    }
                }

                BuildSystemInteger buildSystemBuildId = new BuildSystemInteger(buildId, BuildSystem.koji);
                KojiBuild build = builds.get(buildSystemBuildId);
                if (build == null) {
                    build = allKojiBuilds.get(buildId);
                    if (build != null) {
                        builds.put(buildSystemBuildId, build);

                        if (LOGGER.isInfoEnabled()) {
                            LOGGER.info(
                                    "Found build in Koji: id: {} nvr: {} checksum: ({}) {} filenames: {}",
                                    green(build.getBuildInfo().getId()),
                                    green(build.getBuildInfo().getNvr()),
                                    green(checksum.getType()),
                                    green(checksum.getValue()),
                                    green(String.join(", ", archiveFilenames)));
                        }
                    }
                }
                if (build != null) {
                    // It's ok to not create a new build for the same local archive if it already exists, but we
                    // always need to mark the checksum as found. This handles the scenario where there is a file
                    // present multiple times (with possible different filenames) inside the zip distribution.
                    markFound(entry);
                    addArchiveToBuild(build, archive, filenames);
                } else {
                    LOGGER.warn(
                            "Null build when adding archive id {} and filenames {}",
                            red(archive.getArchiveId()),
                            red(filenames));
                }
            }

            if (listener != null) {
                listener.buildChecked(new BuildCheckedEvent(checksum, BuildSystem.koji));
            }
        }

        /*
         * As a final step, cleanup the buildZero.
         */
        KojiBuild buildZero = builds.get(new BuildSystemInteger(0, BuildSystem.none));

        // XXX: This was meant to be shared between Pnc and Koji, but it appears to add files which are already present
        // buildFinderUtils.addFilesInError(buildZero);

        List<KojiLocalArchive> localArchives = buildZero.getArchives();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "Find parents for {} archives: {}",
                    localArchives.size(),
                    localArchives.stream().map(KojiLocalArchive::toString).collect(Collectors.joining(", ")));
        }

        Iterator<KojiLocalArchive> it = localArchives.iterator();

        while (it.hasNext()) {
            KojiLocalArchive localArchive = it.next();
            Collection<String> filenames = localArchive.getFilenames();

            LOGGER.debug("Handle archive id {} with filenames {}", localArchive.getArchive().getArchiveId(), filenames);

            Iterator<String> it2 = filenames.iterator();

            while (it2.hasNext()) {
                String filename = it2.next();
                Optional<String> optionalParentFilename = handleNotFoundFile(filename);

                if (optionalParentFilename.isPresent() && optionalParentFilename.get().contains(BANG_SLASH)) {
                    LOGGER.debug("Removing {} since we found a parent elsewhere", filename);
                    it2.remove();
                } else {
                    LOGGER.debug("Keeping {} since the parent is the distribution itself", filename);
                }
            }

            if (filenames.isEmpty()) {
                LOGGER.debug("Remove archive since filenames is empty");
                it.remove();
            }
        }

        Utils.shutdownAndAwaitTermination(pool);
        buildsList = new ArrayList<>(builds.values());

        buildsList.sort(Comparator.comparingInt(build -> build.getBuildInfo().getId()));

        buildsFoundList = buildsList.size() > 1 ? buildsList.subList(1, buildsList.size()) : Collections.emptyList();

        return Collections.unmodifiableMap(builds);
    }

    private void markFound(Entry<Checksum, Collection<String>> entry) {
        LOGGER.debug("Mark found checksum: {}", entry);

        foundChecksums.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        notFoundChecksums.remove(entry.getKey());

        KojiBuild buildZero = builds.get(new BuildSystemInteger(0, BuildSystem.none));
        buildZero.getArchives()
                .removeIf(
                        localArchive -> localArchive.getChecksums()
                                .stream()
                                .anyMatch(
                                        cksum -> cksum.getType() == entry.getKey().getType()
                                                && cksum.getValue().equals(entry.getKey().getValue())));

        // The same checksum might be associated with multiple filenames (in case of files present multiple times inside
        // the zip distribution).
        entry.getValue().forEach(this::handleFoundFile);
    }

    private void markNotFound(Entry<Checksum, Collection<String>> entry) {
        LOGGER.debug("Mark not found checksum: {}", entry);
        notFoundChecksums.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        addArchiveWithoutBuild(entry.getKey(), new ArrayList<>(entry.getValue()));
    }

    public Map<BuildSystemInteger, KojiBuild> getBuildsMap() {
        if (builds == null) {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap(builds);
    }

    public List<KojiBuild> getBuildsFound() {
        if (buildsFoundList == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(buildsFoundList);
    }

    public List<KojiBuild> getBuilds() {
        if (buildsList == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(buildsList);
    }

    private KojiBuild findBestBuild(Map<Integer, KojiBuild> allBuilds, List<KojiArchiveInfo> archiveInfos) {
        LOGGER.debug(
                "Find best build for checksum {} filename {} out of {} archives",
                green(archiveInfos.get(0).getChecksum()),
                green(archiveInfos.get(0).getFilename()),
                green(archiveInfos.size()));

        List<KojiBuild> candidateBuilds = getKojiBuildsForArchives(allBuilds, archiveInfos);
        KojiBuild build = findBestBuildFromCandidates(candidateBuilds, archiveInfos);

        LOGGER.debug(
                "Found best build id {} from {} candidates",
                green(build.getBuildInfo().getId()),
                green(candidateBuilds.size()));

        return build;
    }

    private static List<KojiBuild> getKojiBuildsForArchives(
            Map<Integer, KojiBuild> allBuilds,
            List<KojiArchiveInfo> archiveInfos) {
        Set<Integer> buildIds = archiveInfos.stream()
                .map(KojiArchiveInfo::getBuildId)
                .collect(Collectors.toCollection(HashSet::new));
        return buildIds.stream().map(allBuilds::get).collect(Collectors.toUnmodifiableList());
    }

    public Map<Checksum, Collection<String>> getFoundChecksums() {
        return Collections.unmodifiableMap(foundChecksums);
    }

    public Map<Checksum, Collection<String>> getNotFoundChecksums() {
        return Collections.unmodifiableMap(notFoundChecksums);
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public void outputToFile() throws IOException {
        JSONUtils.dumpObjectToFile(builds, new File(outputDirectory, getBuildsFilename()));
    }

    @Override
    public Map<BuildSystemInteger, KojiBuild> call() throws KojiClientException {
        Instant startTime = Instant.now();
        MultiValuedMap<Checksum, String> localchecksumMap = new ArrayListValuedHashMap<>();
        Collection<Checksum> checksums = new HashSet<>();
        Checksum checksum;
        boolean finished = false;
        Map<BuildSystemInteger, KojiBuild> allBuilds = new HashMap<>();

        while (!finished) {
            try {
                checksum = analyzer.getQueue().take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KojiClientException("Error taking from queue", e);
            }

            if (checksum.getValue() == null) {
                break;
            }

            checksums.add(checksum);

            int numElements = analyzer.getQueue().drainTo(checksums);

            LOGGER.debug("Got {} checksums from queue", numElements + 1);

            for (Checksum cksum : checksums) {
                String value = cksum.getValue();

                if (value == null) {
                    finished = true;
                } else {
                    if (cksum.getType() == ChecksumType.md5) {
                        String filename = cksum.getFilename();
                        localchecksumMap.put(cksum, filename);
                    }
                }
            }

            FindBuildsResult pncBuildsNew;
            Map<BuildSystemInteger, KojiBuild> kojiBuildsNew;
            Map<Checksum, Collection<String>> map = localchecksumMap.asMap();

            if (config.getBuildSystems().contains(BuildSystem.pnc) && config.getPncURL() != null) {
                // The preferred checksumType for PNC is sha256, so replace the original map with a preferred map
                LOGGER.debug(
                        "Swapping the original MD5-based checksum map to a SHA256-based checksum map (whenever possible) for finding builds in PNC!");
                Map<Checksum, Collection<String>> sha256BasedCheckumMap = BuildFinderUtils
                        .swapEntriesWithPreferredChecksum(map, analyzer.getFiles(), ChecksumType.sha256);
                LOGGER.debug(
                        "Original MD5-based checksum map: {}, new SHA256-based checksum map: {}",
                        map,
                        sha256BasedCheckumMap);
                try {
                    pncBuildsNew = pncBuildFinder.findBuildsPnc(sha256BasedCheckumMap);
                } catch (RemoteResourceException e) {
                    throw new KojiClientException("Pnc error", e);
                }

                allBuilds.putAll(pncBuildsNew.getFoundBuilds());

                if (!pncBuildsNew.getNotFoundChecksums().isEmpty()) {
                    LOGGER.debug(
                            "Need to search in Brew!! Not found checksums: {}",
                            pncBuildsNew.getNotFoundChecksums());
                    LOGGER.debug(
                            "Swapping back the SHA256-based checksum map to a MD5-based checksum map for finding builds in Brew!");

                    Map<Checksum, Collection<String>> md5BasedNotFoundCheckumMap = BuildFinderUtils
                            .swapEntriesWithPreferredChecksum(
                                    pncBuildsNew.getNotFoundChecksums(),
                                    analyzer.getFiles(),
                                    ChecksumType.md5);

                    LOGGER.debug(
                            "Original SHA256-based not found checksum map: {}",
                            pncBuildsNew.getNotFoundChecksums());
                    LOGGER.debug("New MD5-based not found checksum map: {}", md5BasedNotFoundCheckumMap);

                    kojiBuildsNew = findBuilds(md5BasedNotFoundCheckumMap);
                    allBuilds.putAll(kojiBuildsNew);

                    LOGGER.debug(
                            "Searching again in Brew the not found checksums with a SHA256-based map, to find the missed files (e.g. signed binaries)");
                    LOGGER.debug(
                            "Swapping the MD5-based not found checksum map to a SHA256-based checksum map for finding more builds in Brew!");

                    Map<Checksum, Collection<String>> sha256BasedNotFoundCheckumMap = BuildFinderUtils
                            .swapEntriesWithPreferredChecksum(
                                    notFoundChecksums,
                                    analyzer.getFiles(),
                                    ChecksumType.sha256);

                    LOGGER.debug("Original MD5-based not found checksum map: {}", notFoundChecksums);
                    LOGGER.debug("New SHA256-based not found checksum map: {}", sha256BasedNotFoundCheckumMap);

                    // In case the same checksum has already been processed, remove them from the new checksum map
                    sha256BasedNotFoundCheckumMap.keySet().removeAll(notFoundChecksums.keySet());

                    LOGGER.debug(
                            "New SHA256-based not found checksum map after the removal of already processed checksums: {}",
                            sha256BasedNotFoundCheckumMap);

                    kojiBuildsNew = findBuilds(sha256BasedNotFoundCheckumMap);
                    LOGGER.debug("Found more Brew builds which were missed initially: {}", kojiBuildsNew);
                    allBuilds.putAll(kojiBuildsNew);
                }
            } else {
                kojiBuildsNew = findBuilds(map);
                allBuilds.putAll(kojiBuildsNew);
                LOGGER.debug(
                        "Searching again in Brew the not found checksums with a SHA256-based map, to find the missed files (like the signed binaries)");
                LOGGER.debug(
                        "Swapping the MD5-based not found checksum map to a SHA256-based checksum map for finding more builds in Brew!");

                Map<Checksum, Collection<String>> sha256BasedNotFoundCheckumMap = BuildFinderUtils
                        .swapEntriesWithPreferredChecksum(notFoundChecksums, analyzer.getFiles(), ChecksumType.sha256);

                LOGGER.debug("Original MD5-based not found checksum map: {}", notFoundChecksums);
                LOGGER.debug("New SHA256-based not found checksum map: {}", sha256BasedNotFoundCheckumMap);

                // In case the same checksum has already been processed, remove them from the new checksum map
                sha256BasedNotFoundCheckumMap.keySet().removeAll(notFoundChecksums.keySet());

                LOGGER.debug(
                        "New SHA256-based not found checksum map after the removal of already processed checksums: {}",
                        sha256BasedNotFoundCheckumMap);

                kojiBuildsNew = findBuilds(sha256BasedNotFoundCheckumMap);
                LOGGER.debug("Found more Brew builds which were missed initially: {}", kojiBuildsNew);
                allBuilds.putAll(kojiBuildsNew);
            }

            localchecksumMap.clear();
            checksums.clear();
        }

        int size = allBuilds.size();
        int numBuilds = size >= 1 ? size - 1 : 0;

        if (LOGGER.isInfoEnabled()) {
            Instant endTime = Instant.now();
            Duration duration = Duration.between(startTime, endTime).abs();

            LOGGER.info(
                    "Found {} builds in {} (average: {})",
                    green(numBuilds),
                    green(duration),
                    green(numBuilds > 0 ? duration.dividedBy(numBuilds) : 0));
        }

        Set<LicenseInfo> allLicenses = addLicensesToBuilds(analyzer.getLicensesMap(), allBuilds);
        List<String> uniqueLicenses = allLicenses.stream()
                .map(LicenseInfo::getSpdxLicenseId)
                .sorted()
                .distinct()
                .collect(Collectors.toUnmodifiableList());

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(
                    "Added {} unique SPDX licenses to builds: {}",
                    green(uniqueLicenses.size()),
                    green(String.join(", ", uniqueLicenses)));
            Collection<KojiBuild> values = allBuilds.values();
            List<KojiBuild> buildsWithLicenses = values.stream()
                    .filter(kojiBuild -> !kojiBuild.getLicenses().isEmpty())
                    .collect(Collectors.toUnmodifiableList());
            int numBuildsWithLicenses = buildsWithLicenses.size();
            LOGGER.info(
                    "{} / {} = {}% of builds have license information",
                    green(numBuildsWithLicenses),
                    green(numBuilds),
                    green(Math.round(((double) numBuildsWithLicenses / (double) numBuilds) * 100D)));

            if (LOGGER.isWarnEnabled()) {
                List<KojiBuild> allbuildsList = new ArrayList<>(values);
                LOGGER.warn(
                        "{} builds are missing licenses: {}",
                        red(numBuilds - numBuildsWithLicenses),
                        red(
                                ListUtils.subtract(allbuildsList, buildsWithLicenses)
                                        .stream()
                                        .map(KojiBuild::getBuildInfo)
                                        .map(KojiBuildInfo::getNvr)
                                        .sorted()
                                        .collect(Collectors.joining(", "))));
            }
        }

        return allBuilds;
    }

    private static Set<LicenseInfo> addLicensesToBuilds(
            Map<String, Collection<LicenseInfo>> licensesMap,
            Map<BuildSystemInteger, KojiBuild> allBuilds) {
        Set<Entry<String, Collection<LicenseInfo>>> entries = licensesMap.entrySet();
        Set<LicenseInfo> allLicenses = new TreeSet<>();

        for (Entry<String, Collection<LicenseInfo>> licenseEntry : entries) {
            String filename = StringUtils.removeEnd(licenseEntry.getKey(), BANG_SLASH);
            String parentFilename = filename;
            Optional<KojiBuild> optKojiBuild = findBuildForFilename(parentFilename, allBuilds);
            int index;

            while (optKojiBuild.isEmpty() && (index = parentFilename.lastIndexOf(BANG_SLASH)) != -1) {
                parentFilename = parentFilename.substring(0, index);
                optKojiBuild = findBuildForFilename(parentFilename, allBuilds);
            }

            if (optKojiBuild.isPresent()) {
                KojiBuild build = optKojiBuild.get();
                Collection<LicenseInfo> licenseInfos = licenseEntry.getValue();
                build.getLicenses().addAll(licenseInfos);
                allLicenses.addAll(licenseInfos);
            } else {
                LOGGER.error("No matching build found for file {}", boldRed(filename));
            }
        }

        return Collections.unmodifiableSet(allLicenses);
    }

    private static Optional<KojiBuild> findBuildForFilename(
            String filename,
            Map<BuildSystemInteger, KojiBuild> allBuilds) {
        for (KojiBuild build : allBuilds.values()) {
            Optional<KojiLocalArchive> optArchive = build.getArchives()
                    .stream()
                    .filter(ar -> ar.getFilenames().contains(filename))
                    .findFirst();

            if (optArchive.isPresent()) {
                return Optional.of(build);
            }
        }

        return Optional.empty();
    }

    /**
     * Provide a Supplier version of the Callable. This is useful when using the BuildFinder to obtain a
     * CompletableFuture (via {@link java.util.concurrent.CompletableFuture#supplyAsync(Supplier)})
     *
     * @throws CompletionException if a KojiClientException is thrown
     *
     * @return For each checksum type (key), the checksum values of the files
     */
    @Override
    public Map<BuildSystemInteger, KojiBuild> get() {
        try {
            return call();
        } catch (KojiClientException e) {
            throw new CompletionException(e);
        }
    }

    public void setListener(BuildFinderListener listener) {
        this.listener = listener;

        if (pncBuildFinder != null) {
            pncBuildFinder.setListener(listener);
        }
    }
}
