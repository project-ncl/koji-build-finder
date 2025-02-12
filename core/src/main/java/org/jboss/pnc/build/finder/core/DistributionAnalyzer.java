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

import static java.util.Comparator.reverseOrder;
import static java.util.Map.Entry.comparingByValue;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.jboss.pnc.build.finder.core.AnsiUtils.boldRed;
import static org.jboss.pnc.build.finder.core.AnsiUtils.green;
import static org.jboss.pnc.build.finder.core.AnsiUtils.red;
import static org.jboss.pnc.build.finder.core.LicenseSource.BUNDLE_LICENSE;
import static org.jboss.pnc.build.finder.core.LicenseSource.POM;
import static org.jboss.pnc.build.finder.core.LicenseSource.POM_XML;
import static org.jboss.pnc.build.finder.core.LicenseSource.TEXT;
import static org.jboss.pnc.build.finder.core.LicenseUtils.getBundleLicenseFromManifest;
import static org.jboss.pnc.build.finder.core.LicenseUtils.getFirstNonBlankString;
import static org.jboss.pnc.build.finder.core.LicenseUtils.isManifestMfFileName;
import static org.jboss.pnc.build.finder.core.LicenseUtils.isUrl;
import static org.jboss.pnc.build.finder.core.MavenUtils.getLicenses;
import static org.jboss.pnc.build.finder.core.MavenUtils.isPom;
import static org.jboss.pnc.build.finder.core.MavenUtils.isPomXml;
import static org.jboss.pnc.build.finder.core.SpdxLicenseUtils.NOASSERTION;
import static org.jboss.pnc.build.finder.core.SpdxLicenseUtils.getCurrentLicenseId;
import static org.jboss.pnc.build.finder.core.SpdxLicenseUtils.getMatchingLicense;
import static org.jboss.pnc.build.finder.core.SpdxLicenseUtils.getNumberOfSPDXLicenses;
import static org.jboss.pnc.build.finder.core.SpdxLicenseUtils.getSPDXLicenseListVersion;
import static org.jboss.pnc.build.finder.core.SpdxLicenseUtils.getSpdxLicenseMapping;
import static org.jboss.pnc.build.finder.core.SpdxLicenseUtils.isLicenseFile;
import static org.jboss.pnc.build.finder.core.SpdxLicenseUtils.isLicenseFileName;
import static org.jboss.pnc.build.finder.core.Utils.BANG_SLASH;
import static org.jboss.pnc.build.finder.core.Utils.byteCountToDisplaySize;
import static org.jboss.pnc.build.finder.core.Utils.getAllErrorMessages;
import static org.jboss.pnc.build.finder.core.Utils.normalizePath;
import static org.jboss.pnc.build.finder.core.Utils.shutdownAndAwaitTermination;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import java.util.TreeMap;
import java.util.TreeSet;
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
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.vfs2.FileExtensionSelector;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.InvertIncludeFileSelector;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.commons.vfs2.provider.http5.Http5FileProvider;
import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
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
    private static final List<String> NON_ARCHIVE_SCHEMES = List.of("tmp", "res", "ram", "file", "http", "https");

    private static final List<String> JAR_EXTENSIONS = List
            .of("jar", "war", "rar", "ear", "sar", "kar", "jdocbook", "jdocbook-style", "plugin");

    private static final String[] JARS_TO_IGNORE = {
            "-sources.jar" + BANG_SLASH,
            "-javadoc.jar" + BANG_SLASH,
            "-tests.jar" + BANG_SLASH };

    private static final String JAR_URI = ".jar" + BANG_SLASH;

    private static final String CHECKSUMS_FILENAME_BASENAME = "checksums-";

    private static final String LICENSES_FILENAME_BASENAME = "licenses";

    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");

    private static final int FILE_ERRORS_SIZE = 2;

    private static final int LOCAL_FILES_SIZE = 44515;

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

    private final Map<String, Collection<LicenseInfo>> licensesMap;

    private String root;

    private BlockingQueue<Checksum> queue;

    private DistributionAnalyzerListener listener;

    public DistributionAnalyzer(List<String> inputs, BuildConfig config) {
        this(inputs, config, null);
    }

    public DistributionAnalyzer(List<String> inputs, BuildConfig config, BasicCacheContainer cacheManager) {
        Map<String, List<String>> mapping = getSpdxLicenseMapping();

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(
                    "Loaded URL mappings for {} SPDX licenses: {}",
                    green(mapping.size()),
                    green(String.join(", ", mapping.keySet())));
        }

        this.inputs = inputs;
        this.config = config;
        checksumTypesToCheck = EnumSet.copyOf(config.getChecksumTypes());
        map = new EnumMap<>(ChecksumType.class);
        licensesMap = new TreeMap<>();
        String licenseListVersion = getSPDXLicenseListVersion();
        int licenseListSize = getNumberOfSPDXLicenses();
        LOGGER.info(
                "Using SPDX License List {} containing {} licenses",
                green(licenseListVersion),
                green(licenseListSize));

        for (ChecksumType checksumType : checksumTypesToCheck) {
            map.put(checksumType, new HashSetValuedHashMap<>()); // TODO: size
        }

        inverseMap = new HashSetValuedHashMap<>(); // TODO: size

        this.cacheManager = cacheManager;

        fileCaches = new EnumMap<>(ChecksumType.class);

        if (cacheManager != null) {
            for (ChecksumType checksumType : checksumTypesToCheck) {
                fileCaches.put(checksumType, cacheManager.getCache("files-" + checksumType));
            }
        }

        level = new AtomicInteger();
        pool = Executors.newWorkStealingPool();
        fileErrors = new ArrayList<>(FILE_ERRORS_SIZE);
    }

    private static boolean isJavaArchive(FileObject fo) {
        return FilenameUtils.isExtension(fo.getName().getBaseName(), JAR_EXTENSIONS);
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
                                    map.get(checksumType).putAll(localMap);

                                    Collection<Entry<String, LocalFile>> entries = localMap.entries();
                                    try {
                                        for (Entry<String, LocalFile> entry : entries) {
                                            inverseMap.put(
                                                    entry.getValue().getFilename(),
                                                    new Checksum(checksumType, entry.getKey(), entry.getValue()));
                                        }
                                    } catch (ClassCastException e) {
                                        if (LOGGER.isErrorEnabled()) {
                                            LOGGER.error(
                                                    "Error loading cache {}: {}. The cache format has changed"
                                                            + " and you will have to manually delete the existing cache",
                                                    boldRed(ConfigDefaults.CACHE_LOCATION),
                                                    boldRed(getAllErrorMessages(e)));
                                        }

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
                                    if (LOGGER.isInfoEnabled()) {
                                        LOGGER.info(
                                                "Loaded {} checksums for file: {} (checksum: {}) from cache",
                                                green(size),
                                                green(normalizePath(fo, root)),
                                                green(value));
                                    }
                                } else {
                                    if (LOGGER.isInfoEnabled()) {
                                        LOGGER.info(
                                                "File: {} (checksum: {}) not found in cache",
                                                green(normalizePath(fo, root)),
                                                green(value));
                                    }
                                }
                            }
                        }
                    }

                    if (!checksumTypesToCheck.isEmpty()) {
                        if (LOGGER.isInfoEnabled()) {
                            LOGGER.info(
                                    "Finding checksums: {} for file: {}",
                                    green(
                                            String.join(
                                                    ", ",
                                                    checksumTypesToCheck.stream()
                                                            .map(String::valueOf)
                                                            .collect(Collectors.toUnmodifiableSet()))),
                                    green(normalizePath(fo, root)));
                        }

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
                    } else {
                        listChildren(fo);
                    }
                }
            }
        } finally {
            try {
                cleanupVfsCache();
            } catch (IOException e) {
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn("Cleaning up VFS cache failed: {}", red(getMessage(e)));
                }

                LOGGER.debug("Cleaning up VFS cache failed", e);
            }

            shutdownAndAwaitTermination(pool);
        }

        int numChecksums = map.values().iterator().next().size();

        if (LOGGER.isInfoEnabled()) {
            List<String> totalLicenses = licensesMap.values()
                    .stream()
                    .flatMap(Collection::stream)
                    .map(LicenseInfo::getSpdxLicenseId)
                    .toList();
            Set<String> uniqueLicenses = new TreeSet<>(totalLicenses);
            Map<String, Long> licenseCountMap = totalLicenses.stream().collect(groupingBy(identity(), counting()));
            String licenseCounts = licenseCountMap.entrySet()
                    .stream()
                    .sorted(comparingByValue(reverseOrder()))
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .collect(Collectors.joining(", "));
            LOGGER.info(
                    "Found {} unique SPDX licenses (out of {} total SPDX licenses found): {}",
                    green(uniqueLicenses.size()),
                    green(totalLicenses.size()),
                    green(licenseCounts));
            Instant endTime = Instant.now();
            Duration duration = Duration.between(startTime, endTime).abs();
            LOGGER.info(
                    "Total number of checksums: {}, time: {}, average: {}",
                    green(numChecksums),
                    green(duration),
                    green((double) numChecksums > 0.0D ? duration.dividedBy(numChecksums) : 0.0D));

        }

        if (listener != null) {
            listener.checksumsComputed(new ChecksumsComputedEvent(numChecksums));
        }

        return Collections.unmodifiableMap(map);
    }

    private static void cleanupVfsCache() throws IOException {
        // XXX: <https://issues.apache.org/jira/browse/VFS-634>
        String tmpDir = System.getProperty("java.io.tmpdir");

        if (tmpDir != null) {
            Path vfsCacheDir = Path.of(tmpDir, "vfs_cache").toAbsolutePath();

            try (Stream<Path> stream = Files.walk(vfsCacheDir)) {
                List<Path> paths = stream.sorted(reverseOrder()).toList();

                for (Path path : paths) {
                    Files.delete(path);
                }
            }
        }
    }

    private static FileSystemManager createManager() throws FileSystemException {
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
                    green(String.join(", ", sfs.getSchemes())));
        }

        return sfs;
    }

    private static FileObject getFileObjectOfFile(FileSystemManager manager, String input) throws IOException {
        FileObject fo;

        try {
            URI uri = URI.create(input);
            fo = manager.resolveFile(uri);
        } catch (IllegalArgumentException | FileSystemException e) {
            Path path = Path.of(input);

            if (!Files.exists(path) || !Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new IOException("Input path " + path + " does not exist, is not a file, or is not readable");
            }

            fo = manager.resolveFile(path.toUri());
        }

        if (LOGGER.isInfoEnabled()) {
            String filename = fo.getPublicURIString();

            if (fo.isFile()) {
                LOGGER.info("Analyzing: {} ({})", green(filename), green(byteCountToDisplaySize(fo)));
            } else {
                LOGGER.info("Analyzing: {}", green(filename));
            }
        }

        return fo;
    }

    private static boolean isArchive(FileObject fo) {
        FileSystemManager manager = fo.getFileSystem().getFileSystemManager();

        return !NON_ARCHIVE_SCHEMES.contains(fo.getName().getExtension())
                && Stream.of(manager.getSchemes()).anyMatch(s -> s.equals(fo.getName().getExtension()));
    }

    private boolean isDistributionArchive(FileObject fo) {
        return level.intValue() == 1 && !isJavaArchive(fo);
    }

    private boolean isTarArchive(FileObject fo) throws FileSystemException {
        FileObject parent = fo.getParent();

        return level.intValue() == 2 && parent.isFolder() && parent.getName().getFriendlyURI().endsWith(BANG_SLASH)
                && parent.getChildren().length == 1;
    }

    private boolean shouldListArchive(FileObject fo) throws FileSystemException {
        return Boolean.FALSE.equals(config.getDisableRecursion()) || isDistributionArchive(fo) || isTarArchive(fo);
    }

    private void listArchive(FileObject fo) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Creating file system for: {}", normalizePath(fo, root));
        }

        FileSystem fileSystem = fo.getFileSystem();
        FileSystemManager manager = fileSystem.getFileSystemManager();
        FileObject layered;
        FileSystem fs = null;

        try {
            layered = manager.createFileSystem(fo.getName().getExtension(), fo);
            fs = layered.getFileSystem();
            listChildren(layered);
        } catch (IOException e) {
            String filename = normalizePath(fo, root);
            String message = getMessage(e);
            fileErrors.add(new FileError(filename, message));
            LOGGER.warn("Unable to process archive/compressed file: {}: {}", red(filename), red(message));
            LOGGER.debug("Error", e);
        } finally {
            if (fs != null) {
                manager.closeFileSystem(fs);
            }
        }
    }

    private static String getMessage(Throwable t) {
        StringBuilder sb = new StringBuilder(32);

        if (t instanceof java.nio.file.FileSystemException fse) {

            if (fse instanceof DirectoryNotEmptyException) {
                if (!sb.isEmpty()) {
                    sb.append(": ");
                }

                sb.append("directory ").append(fse.getFile()).append(" not empty");
            }
        } else {
            sb.append(getAllErrorMessages(t));
        }

        return sb.toString();
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

        return !excludeFile && !excludeExtension;
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
        List<FileObject> pomFiles = new ArrayList<>(LOCAL_FILES_SIZE);
        List<FileObject> localFiles = new ArrayList<>(LOCAL_FILES_SIZE);

        try {
            FileExtensionSelector pomSelector = new FileExtensionSelector("pom");
            fo.findFiles(pomSelector, true, pomFiles);
            fo.findFiles(new InvertIncludeFileSelector(pomSelector), true, localFiles);
            pomFiles.addAll(localFiles);
            int numChildren = pomFiles.size();
            Iterable<Future<Set<Checksum>>> futures;
            Collection<Callable<Set<Checksum>>> tasks = new ArrayList<>(numChildren);

            if (isMainJar(fo)) {
                List<LicenseInfo> licenseInfos = addLicensesFromJar(fo, pomFiles);
                putLicenses(Utils.normalizePath(fo, root), licenseInfos);
            }

            for (FileObject file : pomFiles) {
                if (file.isFile()) {
                    if (!checksumTypesToCheck.isEmpty()) {
                        if (includeFile(file)) {
                            if ("tar".equals(file.getName().getScheme())) {
                                Future<Set<Checksum>> future = pool.submit(checksumTask(file));
                                handleFutureChecksum(future);
                            } else {
                                tasks.add(checksumTask(file));
                            }
                        }
                    }

                    boolean pom = isPom(file);
                    boolean pomXml = isPomXml(file);

                    if (pom || pomXml) {
                        LicenseSource source = pom ? POM : POM_XML;
                        List<LicenseInfo> licenseInfos = addLicensesFromPom(file, source);

                        try {
                            Map<String, List<LicenseInfo>> map = getLicenses(root, file, source);
                            putLicenses(map.keySet().iterator().next(), licenseInfos);
                        } catch (XmlPullParserException | InterpolationException e) {
                            if (LOGGER.isErrorEnabled()) {
                                LOGGER.error(
                                        "Error parsing POM file {}: {}",
                                        boldRed(file),
                                        boldRed(getAllErrorMessages(e)));
                            }
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

    private static boolean isMainJar(FileObject fo) {
        String name = fo.getPublicURIString();
        return name.endsWith(JAR_URI) && !StringUtils.endsWithAny(name, JARS_TO_IGNORE);
    }

    private List<LicenseInfo> addLicensesFromJar(FileObject jar, List<FileObject> localFiles) {
        return localFiles.parallelStream()
                .map(localFile -> addLicensesFromJar(jar, localFile))
                .filter(not(Collection::isEmpty))
                .flatMap(Collection::stream)
                .toList();
    }

    private List<LicenseInfo> addLicensesFromJar(FileObject jar, FileObject localFile) {
        List<LicenseInfo> licenseInfos;

        try {
            if (isPomXml(localFile)) {
                licenseInfos = addLicensesFromPom(localFile, POM_XML);
            } else if (isManifestMfFileName(localFile)) {
                licenseInfos = addLicensesFromBundleLicense(localFile);
            } else if (isLicenseFile(localFile)) {
                licenseInfos = addLicenseFromTextFile(jar, localFile);
            } else {
                licenseInfos = Collections.emptyList();
            }
        } catch (IOException e) {
            licenseInfos = Collections.emptyList();
        }

        // Second license pass which looks for relative file URLs, e.g., names such as META-INF/LICENSE
        licenseInfos.stream()
                .filter(licenseInfo -> NOASSERTION.equals(licenseInfo.getSpdxLicenseId()))
                .forEach(licenseInfo -> handleRelativeURL(jar, localFile, licenseInfo));

        // If there are any licenses still unmatched, print them, but ignore unmatched files that were already checked
        // in the last step
        if (LOGGER.isWarnEnabled()) {
            licenseInfos.stream()
                    .filter(licenseInfo -> NOASSERTION.equals(licenseInfo.getSpdxLicenseId()))
                    .forEach(licenseInfo -> checkMissingMapping(localFile, licenseInfo));
        }

        return Collections.unmodifiableList(licenseInfos);
    }

    private static List<LicenseInfo> addLicenseFromTextFile(FileObject jar, FileObject licenseFile) throws IOException {
        String licenseId = getMatchingLicense(licenseFile);
        LicenseInfo licenseInfo = new LicenseInfo(
                null,
                jar.getName().getRelativeName(licenseFile.getName()),
                getCurrentLicenseId(licenseId),
                TEXT);
        return Collections.singletonList(licenseInfo);
    }

    private static List<LicenseInfo> addLicensesFromBundleLicense(FileObject fileObject) throws IOException {
        List<LicenseInfo> licenses = new ArrayList<>(3);
        List<BundleLicense> bundlesLicenses = getBundleLicenseFromManifest(fileObject);

        for (BundleLicense bundleLicense : bundlesLicenses) {
            String licenseIdentifier = bundleLicense.getLicenseIdentifier();
            String description = bundleLicense.getDescription();
            String name = getFirstNonBlankString(licenseIdentifier, description);
            String url = bundleLicense.getLink();
            LicenseInfo licenseInfo = new LicenseInfo(name, url, BUNDLE_LICENSE);
            licenses.add(licenseInfo);
        }

        return Collections.unmodifiableList(licenses);
    }

    private List<LicenseInfo> addLicensesFromPom(FileObject fileObject, LicenseSource source) throws IOException {
        try {
            Map<String, List<LicenseInfo>> map = getLicenses(root, fileObject, source);
            Entry<String, List<LicenseInfo>> entry = map.entrySet().iterator().next();
            String pomOrJarFile = entry.getKey();
            List<LicenseInfo> licenseInfos = entry.getValue();

            if (licenseInfos.isEmpty()) {
                return Collections.emptyList();
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "Found {} SPDX licenses for {}: {}",
                        licenseInfos.size(),
                        pomOrJarFile,
                        String.join(
                                ", ",
                                licenseInfos.stream()
                                        .map(LicenseInfo::getSpdxLicenseId)
                                        .collect(Collectors.toUnmodifiableSet())));
            }

            return Collections.unmodifiableList(licenseInfos);
        } catch (XmlPullParserException | InterpolationException e) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error(
                        "Unable to read licenses from file {}: {}",
                        boldRed(fileObject),
                        boldRed(getAllErrorMessages(e)));
            }

            throw new IOException(e);
        }
    }

    private void checkMissingMapping(FileObject localFile, LicenseInfo licenseInfo) {
        String name = licenseInfo.getName();
        String url = licenseInfo.getUrl();

        if (name == null && url == null) {
            return;
        }

        if (isLicenseFileName(name)) {
            return;
        }

        if (isLicenseFileName(url)) {
            return;
        }

        if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(
                    "Missing SPDX license mapping for name: {}, URL: {}, filename: {}",
                    red(name),
                    red(url),
                    red(normalizePath(localFile, root)));
        }
    }

    private void handleRelativeURL(FileObject jar, FileObject localFile, LicenseInfo licenseInfo) {
        String name = licenseInfo.getName();
        String url = licenseInfo.getUrl();

        if (name == null && url == null) {
            return;
        }

        // URL is not relative, ignore
        if (isUrl(url)) {
            return;
        }

        if (name == null) {
            name = url;
        }

        try {
            // If the URL is absent, consider the possibility that the name refers to a file inside the JAR
            FileObject licenseFile = jar.resolveFile(name);

            if (!isLicenseFile(licenseFile)) {
                return;
            }

            if (licenseFile.isFolder()) {
                return;
            }

            if (!licenseFile.isReadable()) {
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn(
                            "License file {} from {} is not a file or is not readable",
                            red(name),
                            red(normalizePath(localFile, root)));
                }

                return;
            }

            List<LicenseInfo> licenseInfos = addLicenseFromTextFile(jar, licenseFile);

            if (licenseInfos.isEmpty()) {
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn(
                            "Failed to add licenses from file {} located in JAR {}",
                            red(normalizePath(licenseFile, root)),
                            red(normalizePath(jar, root)));
                }

                return;
            }

            // XXX: Currently, the API returns either 0 or 1 licenses, which we rely on here
            LicenseInfo licenseInfo2 = licenseInfos.get(0);
            String spdxLicenseId = licenseInfo2.getSpdxLicenseId();
            licenseInfo.setSpdxLicense(spdxLicenseId);
        } catch (IOException e) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error(
                        "Error adding relative license URL name {} for {}: {}",
                        boldRed(name),
                        boldRed(normalizePath(jar, root)),
                        boldRed(getAllErrorMessages(e)));
            }
        }
    }

    private void putLicenses(String pomOrJarFile, Collection<LicenseInfo> licenseInfos) {
        Collection<LicenseInfo> existingLicenses = licensesMap.get(pomOrJarFile);

        if (existingLicenses != null) {
            existingLicenses.addAll(licenseInfos);
        } else {
            licensesMap.put(pomOrJarFile, licenseInfos);
        }
    }

    public List<String> getInputs() {
        return Collections.unmodifiableList(inputs);
    }

    public Path getChecksumFile(ChecksumType checksumType) {
        return Path.of(config.getOutputDirectory(), CHECKSUMS_FILENAME_BASENAME + checksumType + ".json");
    }

    public Path getLicensesFile() {
        return Path.of(config.getOutputDirectory(), LICENSES_FILENAME_BASENAME + ".json");
    }

    public Map<String, Collection<LicenseInfo>> getLicensesMap() {
        return Collections.unmodifiableMap(licensesMap);
    }

    public void outputToFile(ChecksumType checksumType) throws IOException {
        JSONUtils.dumpObjectToFile(getChecksums(checksumType), getChecksumFile(checksumType));
    }

    public void outputLicensesToFile() throws IOException {
        JSONUtils.dumpObjectToFile(getLicensesMap(), getLicensesFile());
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
     * @throws CompletionException if an IO exception is thrown
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
