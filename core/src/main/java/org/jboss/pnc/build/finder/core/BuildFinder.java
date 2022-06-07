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

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.infinispan.commons.api.BasicCacheContainer;
import org.jboss.pnc.build.finder.koji.ClientSession;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.pnc.client.PncClient;
import org.jboss.pnc.client.RemoteResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.koji.KojiClientException;

public class BuildFinder
        implements Callable<Map<BuildSystemInteger, KojiBuild>>, Supplier<Map<BuildSystemInteger, KojiBuild>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildFinder.class);

    private static final String BUILDS_FILENAME = "builds.json";

    private static final String CHECKSUMS_FILENAME_BASENAME = "checksums-";

    private final ClientSession session;

    private final BuildConfig config;

    private final KojiBuildFinder kojiBuildFinder;

    private final DistributionAnalyzer analyzer;

    private File outputDirectory;

    private final PncBuildFinder pncBuildFinder;

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
        this.analyzer = analyzer;

        BuildFinderUtils buildFinderUtils = new BuildFinderUtils(config, analyzer, session);
        this.pncBuildFinder = new PncBuildFinder(pncclient, buildFinderUtils, config);

        this.kojiBuildFinder = new KojiBuildFinder(session, config, analyzer, cacheManager);
    }

    public static String getChecksumFilename(ChecksumType checksumType) {
        return CHECKSUMS_FILENAME_BASENAME + checksumType + ".json";
    }

    public static String getBuildsFilename() {
        return BUILDS_FILENAME;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public KojiBuildFinder getKojiBuildFinder() {
        return kojiBuildFinder;
    }

    public void outputToFile() throws IOException {
        JSONUtils.dumpObjectToFile(kojiBuildFinder.getBuilds(), new File(outputDirectory, getBuildsFilename()));
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
                try {
                    pncBuildsNew = pncBuildFinder.findBuildsPnc(map);
                } catch (RemoteResourceException e) {
                    throw new KojiClientException("Pnc error", e);
                }

                allBuilds.putAll(pncBuildsNew.getFoundBuilds());

                if (!pncBuildsNew.getNotFoundChecksums().isEmpty()) {
                    kojiBuildsNew = kojiBuildFinder.findBuilds(pncBuildsNew.getNotFoundChecksums());
                    allBuilds.putAll(kojiBuildsNew);
                }
            } else {
                kojiBuildsNew = kojiBuildFinder.findBuilds(map);
                allBuilds.putAll(kojiBuildsNew);
            }

            localchecksumMap.clear();
            checksums.clear();
        }

        if (LOGGER.isInfoEnabled()) {
            int size = allBuilds.size();
            int numBuilds = size >= 1 ? size - 1 : 0;
            Instant endTime = Instant.now();
            Duration duration = Duration.between(startTime, endTime).abs();

            LOGGER.info(
                    "Found {} builds in {} (average: {})",
                    green(numBuilds),
                    green(duration),
                    green(numBuilds > 0 ? duration.dividedBy((long) numBuilds) : 0));
        }

        return allBuilds;
    }

    /**
     * Provide a Supplier version of the Callable. This is useful when using the BuildFinder to obtain a
     * CompletableFuture (via {@link java.util.concurrent.CompletableFuture#supplyAsync(Supplier)})
     *
     * throws CompletionException if a KojiClientException is thrown
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
}
