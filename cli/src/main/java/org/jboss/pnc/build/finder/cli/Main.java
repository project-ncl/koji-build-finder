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
package org.jboss.pnc.build.finder.cli;

import static org.jboss.pnc.build.finder.core.AnsiUtils.boldRed;
import static org.jboss.pnc.build.finder.core.AnsiUtils.boldYellow;
import static org.jboss.pnc.build.finder.core.AnsiUtils.green;
import static org.jboss.pnc.build.finder.core.AnsiUtils.red;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.infinispan.commons.util.Version;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationChildBuilder;
import org.infinispan.jboss.marshalling.commons.GenericJBossMarshaller;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.pnc.build.finder.core.BuildConfig;
import org.jboss.pnc.build.finder.core.BuildFinder;
import org.jboss.pnc.build.finder.core.BuildSystem;
import org.jboss.pnc.build.finder.core.BuildSystemInteger;
import org.jboss.pnc.build.finder.core.Checksum;
import org.jboss.pnc.build.finder.core.ChecksumType;
import org.jboss.pnc.build.finder.core.ConfigDefaults;
import org.jboss.pnc.build.finder.core.DistributionAnalyzer;
import org.jboss.pnc.build.finder.core.JSONUtils;
import org.jboss.pnc.build.finder.core.Utils;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiClientSession;
import org.jboss.pnc.build.finder.koji.KojiJSONUtils;
import org.jboss.pnc.build.finder.pnc.client.HashMapCachingPncClient;
import org.jboss.pnc.build.finder.pnc.client.PncClient;
import org.jboss.pnc.build.finder.report.Report;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.koji.KojiClientException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        abbreviateSynopsis = true,
        description = "Finds builds in Koji and PNC.",
        mixinStandardHelpOptions = true,
        name = "build-finder",
        showDefaultValues = true,
        showEndOfOptionsDelimiterInUsageHelp = true,
        versionProvider = Main.ManifestVersionProvider.class)
public final class Main implements Callable<Void> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private ExecutorService pool;

    private EmbeddedCacheManager cacheManager;

    @Spec
    private CommandSpec commandSpec;

    @Option(
            names = { "-a", "--archive-type" },
            paramLabel = "STRING",
            description = "Add a Koji archive type to check.",
            converter = FilenameConverter.class)
    private List<String> archiveTypes = ConfigDefaults.ARCHIVE_TYPES;

    @Option(
            names = { "-b", "--build-system" },
            paramLabel = "BUILD_SYSTEM",
            description = "Add a build system (${COMPLETION-CANDIDATES}).")
    private List<BuildSystem> buildSystems = ConfigDefaults.BUILD_SYSTEMS;

    @Option(names = "--cache-lifespan", paramLabel = "LONG", description = "Specify cache lifespan.")
    private Long cacheLifespan = ConfigDefaults.CACHE_LIFESPAN;

    @Option(names = "--cache-max-idle", paramLabel = "LONG", description = "Specify cache maximum idle time.")
    private Long cacheMaxIdle = ConfigDefaults.CACHE_MAX_IDLE;

    @Option(names = { "-c", "--config" }, paramLabel = "FILE", description = "Specify configuration file to use.")
    private File configFile = new File(ConfigDefaults.CONFIG);

    @Option(names = { "-d", "--debug" }, description = "Enable debug logging.")
    private boolean debug;

    @Option(names = "--disable-cache", description = "Disable local cache.")
    private Boolean disableCache = ConfigDefaults.DISABLE_CACHE;

    @Option(names = "--disable-recursion", description = "Disable recursion.")
    private Boolean disableRecursion = ConfigDefaults.DISABLE_RECURSION;

    @Option(
            names = { "-e", "--archive-extension" },
            paramLabel = "STRING",
            description = "Add a Koji archive type extension to check.",
            converter = FilenameConverter.class)
    private List<String> archiveExtensions = ConfigDefaults.ARCHIVE_EXTENSIONS;

    @Option(names = { "-k", "--checksum-only" }, description = "Only checksum files and do not find builds.")
    private Boolean checksumOnly = ConfigDefaults.CHECKSUM_ONLY;

    @Option(names = "--koji-hub-url", paramLabel = "URL", description = "Set Koji hub URL.")
    private URL kojiHubURL = ConfigDefaults.KOJI_HUB_URL;

    @Option(names = "--koji-multicall-size", paramLabel = "INT", description = "Set Koji multicall size.")
    private Integer kojiMulticallSize = ConfigDefaults.KOJI_MULTICALL_SIZE;

    @Option(names = "--koji-num-threads", paramLabel = "INT", description = "Set Koji num threads.")
    private Integer kojiNumThreads = ConfigDefaults.KOJI_NUM_THREADS;

    @Option(names = "--koji-web-url", paramLabel = "URL", description = "Set Koji web URL.")
    private URL kojiWebURL = ConfigDefaults.KOJI_WEB_URL;

    @Option(names = "--krb-ccache", paramLabel = "FILE", description = "Set location of Kerberos credential cache.")
    private File krbCCache;

    @Option(names = "--krb-keytab", paramLabel = "FILE", description = "Set location of Kerberos keytab.")
    private File krbKeytab;

    @Option(
            names = "--krb-password",
            paramLabel = "STRING",
            description = "Set Kerberos password.",
            arity = "0..1",
            interactive = true)
    private String krbPassword;

    @Option(names = "--krb-principal", paramLabel = "STRING", description = "Set Kerberos client principal.")
    private String krbPrincipal;

    @Option(names = "--krb-service", paramLabel = "STRING", description = "Set Kerberos client service.")
    private String krbService;

    @Option(names = { "-o", "--output-directory" }, paramLabel = "FILE", description = "Set output directory.")
    private File outputDirectory = new File(ConfigDefaults.OUTPUT_DIR);

    @Option(names = "--pnc-partition-size", paramLabel = "INT", description = "Set Pnc partition size.")
    private Integer pncPartitionSize = ConfigDefaults.PNC_PARTITION_SIZE;

    @Option(names = "--pnc-url", paramLabel = "URL", description = "Set Pnc URL.")
    private URL pncURL = ConfigDefaults.PNC_URL;

    @Option(names = { "-q", "--quiet" }, description = "Disable all logging.")
    private boolean quiet;

    @Option(
            names = { "-t", "--checksum-type" },
            paramLabel = "CHECKSUM",
            description = "Add a checksum type (${COMPLETION-CANDIDATES}).")
    private Set<ChecksumType> checksumTypes = ConfigDefaults.CHECKSUM_TYPES;

    @Option(names = "--use-builds-file", description = "Use builds file.")
    private Boolean useBuildsFile = ConfigDefaults.USE_BUILDS_FILE;

    @Option(names = "--use-checksums-file", description = "Use checksums file.")
    private Boolean useChecksumsFile = ConfigDefaults.USE_CHECKSUMS_FILE;

    @Option(
            names = { "-x", "--exclude" },
            paramLabel = "PATTERN",
            description = "Add a pattern to exclude from build lookup.")
    private List<Pattern> excludes = ConfigDefaults.EXCLUDES;

    @Parameters(arity = "1..*", paramLabel = "FILE", description = "One or more files.")
    private List<String> files;

    public static void main(String... args) {
        Main main = new Main();

        try {
            Ansi ansi;
            String property = System.getProperty("picocli.ansi");

            if (property == null) {
                ansi = Ansi.ON;
            } else {
                ansi = Boolean.getBoolean(property) ? Ansi.ON : Ansi.OFF;
            }

            CommandLine commandLine = new CommandLine(main).setColorScheme(Help.defaultColorScheme(ansi));
            int exitCode = commandLine.execute(args);
            System.exit(exitCode);
        } catch (picocli.CommandLine.ExecutionException e) {
            LOGGER.error("Error: {}", boldRed(e.getMessage()), e);
            System.exit(1);
        } finally {
            main.closeCaches();
            main.shutdownPool();
        }
    }

    private static void disableLogging() {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.OFF);
    }

    private static void enableDebugLogging() {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(Logger.ROOT_LOGGER_NAME);

        rootLogger.setLevel(Level.DEBUG);

        LoggerContext loggerContext = rootLogger.getLoggerContext();
        List<ch.qos.logback.classic.Logger> loggerList = loggerContext.getLoggerList();

        for (ch.qos.logback.classic.Logger logger : loggerList) {
            logger.setLevel(Level.DEBUG);
        }

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        encoder.start();

        ConsoleAppender<ILoggingEvent> appender = (ConsoleAppender<ILoggingEvent>) rootLogger.getAppender("STDOUT");

        if (appender != null) {
            appender.setContext(loggerContext);
            appender.setEncoder(encoder);
            appender.start();
        }
    }

    private BuildConfig setupBuildConfig() throws IOException {
        ClassLoader cl = Main.class.getClassLoader();
        BuildConfig defaults = BuildConfig.load(cl);
        BuildConfig config;

        if (configFile.exists()) {
            LOGGER.info("Using configuration file: {}", green(configFile));

            if (defaults == null) {
                LOGGER.info("No configuration file found on classpath");
                config = BuildConfig.load(configFile);
            } else {
                LOGGER.debug("Configuration file found using class loader {}", cl);
                LOGGER.info("Merging with configuration file found on classpath");

                config = BuildConfig.merge(defaults, configFile);
            }
        } else {
            if (defaults == null) {
                LOGGER.info(
                        "Configuration file {} does not exist. Implicitly creating with defaults.",
                        green(configFile));
                config = new BuildConfig();
            } else {
                LOGGER.info(
                        "Configuration file {} does not exist. Implicitly creating using defaults from file {} on classpath.",
                        green(configFile),
                        green(ConfigDefaults.CONFIG_FILE));
                config = defaults;
            }
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("-b")) {
            config.setBuildSystems(buildSystems);
            LOGGER.info("Using build systems: {}", green(buildSystems));
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--cache-lifespan")) {
            config.setCacheLifespan(cacheLifespan);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--cache-max-idle")) {
            config.setCacheMaxIdle(cacheMaxIdle);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--disable-cache")) {
            config.setDisableCache(disableCache);
            LOGGER.info("Local cache: {}", green("disabled"));
        } else {
            LOGGER.info(
                    "Local cache: {} ({} {}), lifespan: {}, maxIdle: {}",
                    green("enabled"),
                    green(Version.getBrandName()),
                    green(Version.getVersion()),
                    green(config.getCacheLifespan()),
                    green(config.getCacheMaxIdle()));
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--disable-recursion")) {
            config.setDisableRecursion(disableRecursion);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("-k")) {
            config.setChecksumOnly(checksumOnly);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("-t")) {
            config.setChecksumTypes(checksumTypes);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("-a")) {
            config.setArchiveTypes(archiveTypes);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("-e")) {
            config.setArchiveExtensions(archiveExtensions);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("-x")) {
            config.setExcludes(excludes);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--koji-hub-url")) {
            config.setKojiHubURL(kojiHubURL);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--koji-multicall-size")) {
            config.setKojiMulticallSize(kojiMulticallSize);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--koji-num-threads")) {
            config.setKojiNumThreads(kojiNumThreads);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--koji-web-url")) {
            config.setKojiWebURL(kojiWebURL);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--krb-ccache")) {
            LOGGER.debug("Kerberos ccache: {}", krbCCache);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--krb-keytab")) {
            LOGGER.debug("Kerberos keytab {}", krbKeytab);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--krb-service")) {
            LOGGER.debug("Kerberos service: {}", krbService);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--krb-principal")) {
            LOGGER.debug("Kerberos principal: {}", krbPrincipal);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--krb-password")) {
            LOGGER.debug("Read Kerberos password");
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--pnc-partition-size")) {
            config.setPncPartitionSize(pncPartitionSize);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--pnc-url")) {
            config.setPncURL(pncURL);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--use-builds-file")) {
            config.setUseBuildsFile(useBuildsFile);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("--use-checksums-file")) {
            config.setUseChecksumsFile(useChecksumsFile);
        }

        if (commandSpec.commandLine().getParseResult().hasMatchedOption("-o")) {
            config.setOutputDirectory(outputDirectory.toString());
            LOGGER.info("Output will be stored in directory: {}", green(outputDirectory));
        }

        return config;
    }

    private void initCaches(BuildConfig config) {
        KojiBuild.KojiBuildExternalizer externalizer = new KojiBuild.KojiBuildExternalizer();
        GlobalConfigurationChildBuilder globalConfig = new GlobalConfigurationBuilder();
        String location = new File(ConfigDefaults.CONFIG_PATH, "cache").getAbsolutePath();

        globalConfig.globalState()
                .persistentLocation(location)
                .serialization()
                .marshaller(new GenericJBossMarshaller())
                .addAdvancedExternalizer(externalizer.getId(), externalizer)
                .whiteList()
                .addRegexp(".*")
                .create();

        GlobalConfiguration globalConfiguration = globalConfig.build();
        Configuration configuration = new ConfigurationBuilder().expiration()
                .lifespan(config.getCacheLifespan())
                .maxIdle(config.getCacheMaxIdle())
                .wakeUpInterval(-1L)
                .persistence()
                .passivation(false)
                .addSingleFileStore()
                .segmented(true)
                .shared(false)
                .preload(true)
                .fetchPersistentState(true)
                .purgeOnStartup(false)
                .location(location)
                .build();

        cacheManager = new DefaultCacheManager(globalConfiguration);

        for (ChecksumType checksumType : checksumTypes) {
            cacheManager.defineConfiguration("files-" + checksumType, configuration);
            cacheManager.defineConfiguration("checksums-" + checksumType, configuration);
            cacheManager.defineConfiguration("checksums-pnc-" + checksumType, configuration);
            cacheManager.defineConfiguration("rpms-" + checksumType, configuration);
        }

        cacheManager.defineConfiguration("builds", configuration);
        cacheManager.defineConfiguration("builds-pnc", configuration);
    }

    private void closeCaches() {
        if (cacheManager != null) {
            try {
                cacheManager.close();
            } catch (IOException e) {
                LOGGER.warn("Error closing cache manager: {}", red(e.getMessage()));
                LOGGER.debug("Error", e);
            }
        }
    }

    private void shutdownPool() {
        if (pool != null) {
            Utils.shutdownAndAwaitTermination(pool);
        }
    }

    private static void writeConfiguration(File configFile, BuildConfig config) {
        if (!configFile.exists()) {
            File configDir = configFile.getParentFile();

            if (configDir != null) {
                boolean ret = configDir.mkdirs();

                LOGGER.debug("mkdirs returned {}", ret);
            }

            try {
                JSONUtils.dumpObjectToFile(config, configFile);
            } catch (IOException e) {
                LOGGER.warn("Error writing configuration file: {}", red(e.getMessage()));
                LOGGER.debug("Error", e);
            }
        }
    }

    @Override
    public Void call() throws KojiClientException {
        if (quiet) {
            disableLogging();
        } else if (debug) {
            enableDebugLogging();
        }

        Utils.printBanner();

        BuildConfig config = null;

        try {
            config = setupBuildConfig();
        } catch (IOException e) {
            LOGGER.error("Error reading configuration file: {}", boldRed(e.getMessage()));
            LOGGER.debug("Error", e);
            System.exit(1);
        }

        LOGGER.debug("{}", config);

        if (Boolean.FALSE.equals(config.getChecksumOnly())) {
            if (config.getKojiHubURL() == null) {
                LOGGER.error("Must set koji-hub-url");
                System.exit(1);
            }

            if (config.getKojiWebURL() == null) {
                LOGGER.error("Must set koji-web-url");
                System.exit(1);
            }
        }

        writeConfiguration(configFile, config);

        boolean ret = outputDirectory.mkdirs();

        LOGGER.debug("mkdirs returned {}", ret);

        LOGGER.info(
                "Checksum type: {}",
                green(String.join(", ", checksumTypes.stream().map(String::valueOf).collect(Collectors.toSet()))));

        Map<ChecksumType, MultiValuedMap<String, String>> checksumsFromFile = new EnumMap<>(ChecksumType.class);

        if (Boolean.TRUE.equals(config.getUseChecksumsFile())) {
            for (ChecksumType checksumType : checksumTypes) {
                File checksumFile = new File(outputDirectory, BuildFinder.getChecksumFilename(checksumType));

                if (checksumFile.exists()) {
                    checksumsFromFile.put(checksumType, new ArrayListValuedHashMap<>());

                    LOGGER.info("Loading checksums from file: {}", green(checksumFile));

                    try {
                        Map<String, Collection<String>> subChecksums = JSONUtils.loadChecksumsFile(checksumFile);
                        Set<Entry<String, Collection<String>>> entrySet = subChecksums.entrySet();

                        for (Entry<String, Collection<String>> entry : entrySet) {
                            String key = entry.getKey();
                            Collection<String> values = entry.getValue();

                            for (String value : values) {
                                checksumsFromFile.get(checksumType).put(key, value);
                            }
                        }
                    } catch (IOException e) {
                        LOGGER.error("Error loading checksums file: {}", boldRed(e.getMessage()));
                        LOGGER.debug("Error", e);
                        System.exit(1);
                    }
                } else {
                    LOGGER.error("File {} does not exist", boldRed(checksumFile));
                    System.exit(1);
                }
            }
        }

        Map<ChecksumType, MultiValuedMap<String, String>> checksums = checksumsFromFile;

        if (Boolean.TRUE.equals(checksumOnly)) {
            if (Boolean.FALSE.equals(config.getUseChecksumsFile())) {
                if (cacheManager == null && !config.getDisableCache()) {
                    initCaches(config);
                }

                pool = Executors.newSingleThreadExecutor();

                DistributionAnalyzer analyzer = new DistributionAnalyzer(files, config, cacheManager);
                Future<Map<ChecksumType, MultiValuedMap<String, String>>> futureChecksum = pool.submit(analyzer);

                try {
                    checksums = futureChecksum.get();
                } catch (ExecutionException e) {
                    LOGGER.error("Error getting checksums: {}", boldRed(e.getMessage()), e);
                    LOGGER.debug("Error", e);
                    System.exit(1);
                } catch (InterruptedException e) {
                    LOGGER.warn("Thread interrupted while getting checksums", e);
                    Thread.currentThread().interrupt();
                }

                Set<ChecksumType> keySet = checksums.keySet();

                for (ChecksumType checksumType : keySet) {
                    try {
                        analyzer.outputToFile(checksumType);
                    } catch (IOException e) {
                        LOGGER.error("Error writing checksums file: {}", boldRed(e.getMessage()));
                        LOGGER.debug("Error", e);
                        System.exit(1);
                    }
                }
            } else {
                int numChecksums = checksums.values().iterator().next().size();

                LOGGER.info("Total number of checksums: {}", green(numChecksums));
            }

            if (checksums.isEmpty()) {
                LOGGER.warn("The list of checksums is empty");
            }

            System.exit(0);
        }

        if (config.getPncURL() != null) {
            LOGGER.info("Pnc support: {}", green("enabled"));
        } else {
            LOGGER.info("Pnc support: {}", green("disabled"));
        }

        BuildFinder finder = null;
        Map<BuildSystemInteger, KojiBuild> builds = null;
        File buildsFile = new File(outputDirectory, BuildFinder.getBuildsFilename());

        if (Boolean.TRUE.equals(config.getUseBuildsFile())) {
            if (buildsFile.exists()) {
                LOGGER.info("Loading builds from file: {}", green(buildsFile.getPath()));

                try {
                    builds = KojiJSONUtils.loadBuildsFile(buildsFile);
                } catch (IOException e) {
                    LOGGER.error("Error loading builds file: {}", boldRed(e.getMessage()));
                    LOGGER.debug("Error", e);
                    System.exit(1);
                }
            } else {
                LOGGER.error("File {} does not exist", boldRed(buildsFile));
                System.exit(1);
            }
        } else {
            if (!checksumTypes.contains(ChecksumType.md5)) {
                LOGGER.error("To find builds, you must enable checksum type: {}", boldRed(ChecksumType.md5));
                System.exit(1);
            }

            if (Boolean.TRUE.equals(config.getUseChecksumsFile())) {
                boolean isKerberos = krbService != null && krbPrincipal != null && krbPassword != null
                        || krbCCache != null || krbKeytab != null;

                try (KojiClientSession session = isKerberos
                        ? new KojiClientSession(
                                config.getKojiHubURL(),
                                krbService,
                                krbPrincipal,
                                krbPassword,
                                krbCCache,
                                krbKeytab)
                        : new KojiClientSession(config.getKojiHubURL());
                        PncClient pncClient = config.getPncURL() != null ? new HashMapCachingPncClient(config) : null) {
                    if (isKerberos) {
                        LOGGER.info("Using Koji session with Kerberos service: {}", green(krbService));
                    } else {
                        LOGGER.info("Using anonymous Koji session");
                    }

                    DistributionAnalyzer analyzer = new DistributionAnalyzer(files, config, cacheManager);

                    analyzer.setChecksums(checksums);

                    if (config.getPncURL() != null) {
                        finder = new BuildFinder(session, config, analyzer, cacheManager, pncClient);
                    } else {
                        finder = new BuildFinder(session, config, analyzer, cacheManager);
                    }

                    Map<Checksum, Collection<String>> newMap = new HashMap<>();

                    for (ChecksumType checksumType : checksumTypes) {
                        Map<String, Collection<String>> map = checksums.get(checksumType).asMap();

                        for (Entry<String, Collection<String>> entry : map.entrySet()) {
                            for (String filename : entry.getValue()) {
                                newMap.put(new Checksum(checksumType, entry.getKey(), filename), entry.getValue());
                            }
                        }
                    }

                    finder.setOutputDirectory(outputDirectory);

                    builds = finder.findBuilds(newMap);
                } catch (KojiClientException e) {
                    LOGGER.error("Error finding builds: {}", boldRed(e.getMessage()));
                    LOGGER.debug("Error", e);
                    System.exit(1);
                } catch (Exception e) {
                    LOGGER.error("Unknown error finding builds: {}", boldRed(e.getMessage()));
                    LOGGER.debug("Error", e);
                    System.exit(1);
                }
            } else {
                if (cacheManager == null && !config.getDisableCache()) {
                    initCaches(config);
                }

                pool = Executors.newFixedThreadPool(2);

                DistributionAnalyzer analyzer = new DistributionAnalyzer(files, config, cacheManager);
                Future<Map<ChecksumType, MultiValuedMap<String, String>>> futureChecksum = pool.submit(analyzer);

                boolean isKerberos = krbService != null && krbPrincipal != null && krbPassword != null
                        || krbCCache != null || krbKeytab != null;

                try (KojiClientSession session = isKerberos
                        ? new KojiClientSession(
                                config.getKojiHubURL(),
                                krbService,
                                krbPrincipal,
                                krbPassword,
                                krbCCache,
                                krbKeytab)
                        : new KojiClientSession(config.getKojiHubURL());
                        PncClient pncClient = config.getPncURL() != null ? new HashMapCachingPncClient(config) : null) {
                    if (isKerberos) {
                        LOGGER.info("Using Koji session with Kerberos service: {}", green(krbService));
                    } else {
                        LOGGER.info("Using anonymous Koji session");
                    }

                    if (config.getPncURL() != null) {
                        finder = new BuildFinder(session, config, analyzer, cacheManager, pncClient);
                    } else {
                        finder = new BuildFinder(session, config, analyzer, cacheManager);
                    }

                    finder.setOutputDirectory(outputDirectory);

                    Future<Map<BuildSystemInteger, KojiBuild>> futureBuilds = pool.submit(finder);

                    try {
                        checksums = futureChecksum.get();
                    } catch (ExecutionException e) {
                        LOGGER.error("Error getting checksums: {}", boldRed(e.getMessage()), e);
                        LOGGER.debug("Error", e);
                        System.exit(1);
                    } catch (InterruptedException e) {
                        LOGGER.warn("Thread interrupted while getting checksums", e);
                        Thread.currentThread().interrupt();
                    }

                    Set<ChecksumType> keySet = checksums.keySet();

                    for (ChecksumType checksumType : keySet) {
                        try {
                            analyzer.outputToFile(checksumType);
                        } catch (IOException e) {
                            LOGGER.error("Error writing checksums file: {}", boldRed(e.getMessage()));
                            LOGGER.debug("Error", e);
                            System.exit(1);
                        }
                    }

                    if (checksums.isEmpty()) {
                        LOGGER.warn("The list of checksums is empty");
                    }

                    try {
                        builds = futureBuilds.get();
                    } catch (ExecutionException e) {
                        LOGGER.error("Error getting builds {}", boldRed(e.getMessage()), e);
                        LOGGER.debug("Error", e);
                        System.exit(1);
                    } catch (InterruptedException e) {
                        LOGGER.warn("Thread interrupted while getting builds", e);
                        Thread.currentThread().interrupt();
                    }

                    JSONUtils.dumpObjectToFile(builds, buildsFile);
                } catch (KojiClientException e) {
                    LOGGER.error("Error finding builds: {}", boldRed(e.getMessage()), e);
                    LOGGER.debug("Koji Client Error", e);
                    System.exit(1);
                } catch (Exception e) {
                    LOGGER.error("Error finding builds: {}", boldRed(e.getMessage()), e);
                    LOGGER.debug("Error", e);
                    System.exit(1);
                }
            }
        }

        List<KojiBuild> buildList = builds != null ? new ArrayList<>(builds.values()) : Collections.emptyList();
        KojiBuild buildZero = builds != null ? buildList.get(0) : null;
        int buildListSize = buildList.size();

        if (buildListSize > 1) {
            buildList.sort(Comparator.comparingInt(build -> build.getBuildInfo().getId()));
        }

        if (buildListSize > 1 || buildZero != null && !buildZero.getArchives().isEmpty()) {
            try {
                Report.generateReports(config, buildList, outputDirectory, files);
            } catch (IOException e) {
                LOGGER.error(
                        "Error writing reports for files {} with {} builds to output directory {}",
                        boldRed(files),
                        boldRed(buildListSize),
                        boldRed(outputDirectory));
                LOGGER.debug("Report error", e);
            }

            LOGGER.info("{}", boldYellow("DONE"));
        } else {
            LOGGER.warn("Did not generate any reports since the list of builds is empty");
        }

        return null;
    }

    public ExecutorService getPool() {
        return pool;
    }

    public EmbeddedCacheManager getCacheManager() {
        return cacheManager;
    }

    static class ManifestVersionProvider implements IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[] { Utils.getBuildFinderVersion() + " (SHA: " + Utils.getBuildFinderScmRevision() + ")" };
        }
    }

    static class FilenameConverter implements ITypeConverter<String> {
        private static final Pattern PATTERN = Pattern.compile(".*[/:\"*?<>|]+.*");

        @Override
        public String convert(String value) {
            if (PATTERN.matcher(value).matches()) {
                throw new IllegalArgumentException("Invalid name");
            }

            return value;
        }
    }
}
