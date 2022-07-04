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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.vfs2.AllFileSelector;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.commons.vfs2.provider.http5.Http5FileProvider;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.jboss.pnc.build.finder.protobuf.MultiValuedMapProtobufWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributionAnalyzer implements Callable<Map<ChecksumType, MultiValuedMap<String, LocalFile>>>,
        Supplier<Map<ChecksumType, MultiValuedMap<String, LocalFile>>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DistributionAnalyzer.class);

    /**
     * Ideally, we should be able to use {@link FileSystemManager#canCreateFileSystem} but that relies on an accurate
     * extension map in {@code providers.xml}. Either fix that up manually or exclude non-viable archive schemes.
     * Further, without overriding the {@link java.net.URLConnection#getFileNameMap} the wrong results will be returned
     * for {@code zip}/{@code gz} which is classified as {@code application/octet-stream}.
     */
    private static final List<String> NON_ARCHIVE_SCHEMES = Collections
            .unmodifiableList(Arrays.asList("tmp", "res", "ram", "file", "http", "https"));

    private static final String CHECKSUMS_FILENAME_BASENAME = "checksums-";

    private final List<String> inputs;

    private final MultiValuedMap<String, Checksum> inverseMap;

    private final BuildConfig config;

    private final Map<ChecksumType, BasicCache<String, MultiValuedMapProtobufWrapper<String, LocalFile>>> fileCaches;

    private final BasicCacheContainer cacheManager;

    private final AtomicInteger level;

    private final ExecutorService pool;

    private final Set<ChecksumType> checksumTypesToCheck;

    private final List<FileError> fileErrors;

    private Map<ChecksumType, MultiValuedMap<String, LocalFile>> map;

    private String root;

    private BlockingQueue<Checksum> queue;

    private DistributionAnalyzerListener listener;

    public DistributionAnalyzer(List<String> inputs, BuildConfig config) {
        this(inputs, config, null);
    }

    public DistributionAnalyzer(List<String> inputs, BuildConfig config, BasicCacheContainer cacheManager) {
        this.inputs = inputs;
        this.config = config;
        checksumTypesToCheck = EnumSet.copyOf(config.getChecksumTypes());
        map = new EnumMap<>(ChecksumType.class);

        for (ChecksumType checksumType : checksumTypesToCheck) {
            map.put(checksumType, new HashSetValuedHashMap<>());
        }

        inverseMap = new HashSetValuedHashMap<>();

        this.cacheManager = cacheManager;

        fileCaches = new EnumMap<>(ChecksumType.class);

        for (ChecksumType checksumType : checksumTypesToCheck) {
            if (cacheManager != null) {
                fileCaches.put(checksumType, cacheManager.getCache("files-" + checksumType));
            }
        }

        level = new AtomicInteger();
        pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        fileErrors = new ArrayList<>();
    }

    private static boolean isJar(FileObject fo) {
        String ext = fo.getName().getExtension();
        List<String> exts = Arrays
                .asList("jar", "war", "rar", "ear", "sar", "kar", "jdocbook", "jdocbook-style", "plugin");

        return exts.contains(ext);
    }

    public Map<ChecksumType, MultiValuedMap<String, LocalFile>> checksumFiles() throws IOException {
        Instant startTime = Instant.now();

        try (FileSystemManager manager = createManager()) {
            for (String input : inputs) {

                try (FileObject fo = getFileObjectOfFile(manager, input)) {
                    root = fo.getName()
                            .getFriendlyURI()
                            .substring(0, fo.getName().getFriendlyURI().indexOf(fo.getName().getBaseName()));
                    Set<Checksum> fileChecksums = cacheManager != null
                            ? Checksum.checksum(fo, checksumTypesToCheck, root)
                            : null;

                    if (fileChecksums != null) {
                        Iterator<ChecksumType> it = checksumTypesToCheck.iterator();

                        while (it.hasNext()) {
                            ChecksumType checksumType = it.next();
                            String value = Checksum.findByType(fileChecksums, checksumType)
                                    .map(Checksum::getValue)
                                    .orElse(null);

                            if (value != null) {
                                MultiValuedMap<String, LocalFile> localMap = fileCaches.get(checksumType).get(value);

                                if (localMap != null) {
                                    this.map.get(checksumType).putAll(localMap);

                                    Collection<Entry<String, LocalFile>> entries = localMap.entries();
                                    try {
                                        for (Entry<String, LocalFile> entry : entries) {
                                            inverseMap.put(
                                                    entry.getValue().getFilename(),
                                                    new Checksum(checksumType, entry.getKey(), entry.getValue()));
                                        }
                                    } catch (ClassCastException e) {
                                        LOGGER.error(
                                                "Error loading cache {}: {}. The cache format has changed"
                                                        + " and you will have to manually delete the existing cache",
                                                boldRed(ConfigDefaults.CACHE_LOCATION),
                                                boldRed(e.getMessage()),
                                                e);
                                        throw e;
                                    }

                                    if (queue != null && checksumType == ChecksumType.md5) {
                                        for (Entry<String, LocalFile> entry : entries) {
                                            try {
                                                Checksum checksum = new Checksum(
                                                        checksumType,
                                                        entry.getKey(),
                                                        entry.getValue());
                                                queue.put(checksum);
                                            } catch (InterruptedException e) {
                                                Thread.currentThread().interrupt();
                                                throw new IOException(e);
                                            }
                                        }
                                    }

                                    it.remove();

                                    int size = localMap.size();

                                    if (listener != null) {
                                        listener.checksumsComputed(new ChecksumsComputedEvent(size));
                                    }

                                    LOGGER.info(
                                            "Loaded {} checksums for file: {} (checksum: {}) from cache",
                                            green(size),
                                            green(fo.getName()),
                                            green(value));
                                } else {
                                    LOGGER.info(
                                            "File: {} (checksum: {}) not found in cache",
                                            green(fo.getName()),
                                            green(value));
                                }
                            }
                        }
                    }

                    if (!checksumTypesToCheck.isEmpty()) {
                        LOGGER.info(
                                "Finding checksums: {} for file: {}",
                                green(
                                        String.join(
                                                ", ",
                                                checksumTypesToCheck.stream()
                                                        .map(String::valueOf)
                                                        .collect(Collectors.toSet()))),
                                green(fo.getName()));

                        listChildren(fo);

                        if (fileChecksums != null) {
                            for (ChecksumType checksumType : checksumTypesToCheck) {
                                Optional<Checksum> cksum = Checksum.findByType(fileChecksums, checksumType);

                                if (cksum.isPresent()) {
                                    fileCaches.get(checksumType)
                                            .put(
                                                    cksum.get().getValue(),
                                                    new MultiValuedMapProtobufWrapper<>(map.get(checksumType)));
                                } else {
                                    throw new IOException("Checksum type " + checksumType + " not found");
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            cleanupVfsCache();
            Utils.shutdownAndAwaitTermination(pool);
        }

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime).abs();

        int numChecksums = map.values().iterator().next().size();

        LOGGER.info(
                "Total number of checksums: {}, time: {}, average: {}",
                green(numChecksums),
                green(duration),
                green((double) numChecksums > 0.0D ? duration.dividedBy((long) numChecksums) : 0.0D));

        if (listener != null) {
            listener.checksumsComputed(new ChecksumsComputedEvent(numChecksums));
        }

        return Collections.unmodifiableMap(map);
    }

    private void cleanupVfsCache() throws IOException {
        // XXX: <https://issues.apache.org/jira/browse/VFS-634>
        String tmpDir = System.getProperty("java.io.tmpdir");

        if (tmpDir != null) {
            File vfsCacheDir = new File(tmpDir, "vfs_cache");

            Files.deleteIfExists(vfsCacheDir.toPath());
        }
    }

    private FileSystemManager createManager() throws FileSystemException {
        StandardFileSystemManager sfs = new StandardFileSystemManager();

        sfs.init();

        if (!sfs.hasProvider("http")) {
            sfs.addProvider("http", new Http5FileProvider());
        }

        if (!sfs.hasProvider("https")) {
            sfs.addProvider("https", new Http5FileProvider());
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(
                    "Initialized file system manager {} with schemes: {}",
                    green(sfs.getClass().getSimpleName()),
                    green(Collections.unmodifiableList(Arrays.asList(sfs.getSchemes()))));
        }

        return sfs;
    }

    private FileObject getFileObjectOfFile(FileSystemManager manager, String input) throws IOException {
        FileObject fo;

        try {
            URI uri = URI.create(input);
            fo = manager.resolveFile(uri);
        } catch (IllegalArgumentException | FileSystemException e) {
            File file = new File(input);

            if (!file.exists()) {
                throw new IOException("Input file " + file + " does not exist");
            }

            fo = manager.resolveFile(file.toURI());
        }

        if (LOGGER.isInfoEnabled()) {
            String filename = fo.getPublicURIString();

            if (fo.isFile()) {
                LOGGER.info("Analyzing: {} ({})", green(filename), green(Utils.byteCountToDisplaySize(fo)));
            } else {
                LOGGER.info("Analyzing: {}", green(filename));
            }
        }

        return fo;
    }

    private boolean isArchive(FileObject fo) {
        FileSystemManager manager = fo.getFileSystem().getFileSystemManager();

        return !NON_ARCHIVE_SCHEMES.contains(fo.getName().getExtension())
                && Stream.of(manager.getSchemes()).anyMatch(s -> s.equals(fo.getName().getExtension()));
    }

    private boolean isDistributionArchive(FileObject fo) {
        return level.intValue() == 1 && !isJar(fo);
    }

    private boolean isTarArchive(FileObject fo) throws FileSystemException {
        FileObject parent = fo.getParent();

        return level.intValue() == 2 && parent.isFolder() && parent.getName().getFriendlyURI().endsWith("!/")
                && parent.getChildren().length == 1;
    }

    private boolean shouldListArchive(FileObject fo) throws FileSystemException {
        return Boolean.FALSE.equals(config.getDisableRecursion()) || isDistributionArchive(fo) || isTarArchive(fo);
    }

    private void listArchive(FileObject fo) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Creating file system for: {}", Utils.normalizePath(fo, root));
        }

        FileSystemManager manager = fo.getFileSystem().getFileSystemManager();
        FileObject layered;
        FileSystem fs = null;

        try {
            layered = manager.createFileSystem(fo.getName().getExtension(), fo);
            fs = layered.getFileSystem();

            listChildren(layered);
        } catch (IOException e) {
            String filename = Utils.normalizePath(fo, root);
            String message = e.getMessage();

            if (message == null) {
                message = "";
            }

            Throwable cause = e.getCause();

            if (cause != null) {
                String causeMessage = cause.getMessage();

                if (causeMessage != null) {
                    message += " (" + causeMessage + ")";
                }
            }

            fileErrors.add(new FileError(filename, message));

            LOGGER.warn("Unable to process archive/compressed file: {}: {}", red(filename), red(message));
            LOGGER.debug("Error", e);
        } finally {
            if (fs != null) {
                manager.closeFileSystem(fs);
            }
        }
    }

    private boolean includeFile(FileObject fo) {
        boolean excludeExtension = !config.getArchiveExtensions().isEmpty()
                && config.getArchiveExtensions().stream().noneMatch(x -> x.equals(fo.getName().getExtension()))
                && !"rpm".equals(fo.getName().getExtension());
        boolean excludeFile = false;

        if (!excludeExtension) {
            String friendlyURI = fo.getName().getFriendlyURI();
            excludeFile = !config.getExcludes().isEmpty()
                    && config.getExcludes().stream().map(Pattern::pattern).anyMatch(friendlyURI::matches);
        }

        boolean include = !excludeFile && !excludeExtension;

        // if (LOGGER.isDebugEnabled()) {
        // LOGGER.debug("Include {}: {}", Utils.normalizePath(fo, root), include);
        // }

        return include;
    }

    private Callable<Set<Checksum>> checksumTask(FileObject fo) {
        return () -> Checksum.checksum(fo, checksumTypesToCheck, root);
    }

    private void handleFutureChecksum(Future<Set<Checksum>> future) throws IOException {
        try {
            Set<Checksum> checksums = future.get();

            for (ChecksumType checksumType : checksumTypesToCheck) {
                Optional<Checksum> optionalChecksum = Checksum.findByType(checksums, checksumType);

                if (optionalChecksum.isPresent()) {
                    Checksum checksum = optionalChecksum.get();
                    map.get(checksumType)
                            .put(checksum.getValue(), new LocalFile(checksum.getFilename(), checksum.getFileSize()));
                }
            }

            for (Checksum checksum : checksums) {
                inverseMap.put(checksum.getFilename(), checksum);
            }

            if (queue != null && config.getChecksumTypes().contains(ChecksumType.md5)) {
                try {
                    for (Checksum checksum : checksums) {
                        if (checksum.getType() == ChecksumType.md5) {
                            queue.put(checksum);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } catch (ExecutionException e) {
            throw new IOException(e);
        }
    }

    private void listChildren(FileObject fo) throws IOException {
        List<FileObject> localFiles = new ArrayList<>();

        try {
            fo.findFiles(new AllFileSelector(), true, localFiles);
            int numChildren = localFiles.size();
            Iterable<Future<Set<Checksum>>> futures;
            Collection<Callable<Set<Checksum>>> tasks = new ArrayList<>(numChildren);

            for (FileObject file : localFiles) {
                if (file.isFile()) {
                    if (includeFile(file)) {
                        if ("tar".equals(file.getName().getScheme())) {
                            Future<Set<Checksum>> future = pool.submit(checksumTask(file));
                            handleFutureChecksum(future);
                        } else {
                            tasks.add(checksumTask(file));
                        }
                    }

                    if (isArchive(file)) {
                        level.incrementAndGet();

                        if (shouldListArchive(file)) {
                            listArchive(file);
                        }

                        level.decrementAndGet();
                    }
                }
            }

            int tasksSize = tasks.size();

            if (tasksSize > 0) {
                LOGGER.debug("Number of checksum tasks: {}", tasksSize);

                try {
                    futures = pool.invokeAll(tasks);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }

                for (Future<Set<Checksum>> future : futures) {
                    handleFutureChecksum(future);
                }
            }
        } finally {
            for (FileObject file : localFiles) {
                file.close();
            }
        }
    }

    public List<String> getInputs() {
        return Collections.unmodifiableList(inputs);
    }

    public File getChecksumFile(ChecksumType checksumType) {
        return new File(config.getOutputDirectory(), CHECKSUMS_FILENAME_BASENAME + checksumType + ".json");
    }

    public void outputToFile(ChecksumType checksumType) throws IOException {
        JSONUtils.dumpObjectToFile(getChecksums(checksumType), getChecksumFile(checksumType));
    }

    public Map<String, Collection<Checksum>> getFiles() {
        return Collections.unmodifiableMap(inverseMap.asMap());
    }

    public void setChecksums(Map<ChecksumType, MultiValuedMap<String, LocalFile>> map) {
        this.map = map;
    }

    public Map<String, Collection<LocalFile>> getChecksums(ChecksumType checksumType) {
        return Collections.unmodifiableMap(map.get(checksumType).asMap());
    }

    public Collection<FileError> getFileErrors() {
        return Collections.unmodifiableList(fileErrors);
    }

    public Map<ChecksumType, MultiValuedMap<String, LocalFile>> getChecksums() {
        return Collections.unmodifiableMap(map);
    }

    @Override
    public Map<ChecksumType, MultiValuedMap<String, LocalFile>> call() throws IOException {
        queue = new LinkedBlockingQueue<>();

        checksumFiles();

        try {
            queue.put(new Checksum());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }

        return Collections.unmodifiableMap(map);
    }

    /**
     * Provide a Supplier version of the Callable. This is useful when using the DistributionAnalyzer to obtain a
     * CompletableFuture (via {@link java.util.concurrent.CompletableFuture#supplyAsync(Supplier)})
     *
     * throws CompletionException if an IO exception is thrown
     *
     * @return For each checksum type (key), the checksum values of the files
     */
    @Override
    public Map<ChecksumType, MultiValuedMap<String, LocalFile>> get() {
        try {
            return call();
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    public BlockingQueue<Checksum> getQueue() {
        return queue;
    }

    public void setListener(DistributionAnalyzerListener listener) {
        this.listener = listener;
    }
}
