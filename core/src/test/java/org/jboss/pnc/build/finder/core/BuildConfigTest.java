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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

class BuildConfigTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildConfigTest.class);

    @Test
    void testCopyConfig() throws IOException {
        // given
        Pattern fooPattern = Pattern.compile("foo");
        String fooPatternString = fooPattern.pattern();
        List<Pattern> excludes = Collections.singletonList(fooPattern);
        List<String> baseExtension = Collections.unmodifiableList(Arrays.asList("foo", "bar", "baz"));

        BuildConfig base = new BuildConfig();
        base.setExcludes(excludes);
        base.setArchiveExtensions(baseExtension);

        assertThat(base.getArchiveTypes()).isEqualTo(ConfigDefaults.ARCHIVE_TYPES);
        assertThat(base.getExcludes()).isNotEqualTo(ConfigDefaults.EXCLUDES);
        assertThat(base.getArchiveExtensions()).isEqualTo(baseExtension);

        // when
        List<String> updatedExtensions = Collections.unmodifiableList(Arrays.asList("jar", "zip"));
        List<String> types = Collections.unmodifiableList(Arrays.asList("good", "bad", "ugly"));

        BuildConfig copy = BuildConfig.copy(base);
        copy.setArchiveExtensions(updatedExtensions);
        copy.setArchiveTypes(types);

        // then
        assertThat(copy).isNotSameAs(base);

        assertThat(base.getArchiveTypes()).isEqualTo(ConfigDefaults.ARCHIVE_TYPES);
        assertThat(base.getExcludes()).isNotEqualTo(ConfigDefaults.EXCLUDES);
        assertThat(base.getArchiveExtensions()).isEqualTo(baseExtension);

        assertThat(copy.getExcludes()).hasSize(1);
        Pattern copiedPattern = copy.getExcludes().get(0);
        assertThat(copiedPattern.pattern()).isEqualTo(fooPatternString);
        assertThat(copy.getArchiveExtensions()).isEqualTo(updatedExtensions);
        assertThat(copy.getArchiveTypes()).isEqualTo(types);
    }

    @Test
    void testDefaults() throws JsonProcessingException {
        BuildConfig bc = new BuildConfig();
        String s = JSONUtils.dumpString(bc);

        assertThat(s).isNotNull();

        LOGGER.debug("Default configuration: {}", s);

        assertThat(bc.getArchiveTypes()).isEqualTo(ConfigDefaults.ARCHIVE_TYPES);
        assertThat(bc.getArchiveExtensions()).isEqualTo(ConfigDefaults.ARCHIVE_EXTENSIONS);
        assertThat(bc.getBuildSystems()).isEqualTo(ConfigDefaults.BUILD_SYSTEMS);
        assertThat(bc.getCacheLifespan()).isEqualTo(ConfigDefaults.CACHE_LIFESPAN);
        assertThat(bc.getCacheMaxIdle()).isEqualTo(ConfigDefaults.CACHE_MAX_IDLE);
        assertThat(bc.getChecksumOnly()).isEqualTo(ConfigDefaults.CHECKSUM_ONLY);
        assertThat(bc.getChecksumTypes()).isEqualTo(ConfigDefaults.CHECKSUM_TYPES);
        assertThat(bc.getDisableCache()).isEqualTo(ConfigDefaults.DISABLE_CACHE);
        assertThat(bc.getDisableRecursion()).isEqualTo(ConfigDefaults.DISABLE_RECURSION);
        assertThat(bc.getExcludes()).isEqualTo(ConfigDefaults.EXCLUDES);
        assertThat(bc.getKojiHubURL()).isEqualTo(ConfigDefaults.KOJI_HUB_URL);
        assertThat(bc.getKojiMulticallSize()).isEqualTo(ConfigDefaults.KOJI_MULTICALL_SIZE);
        assertThat(bc.getKojiNumThreads()).isEqualTo(ConfigDefaults.KOJI_NUM_THREADS);
        assertThat(bc.getKojiWebURL()).isEqualTo(ConfigDefaults.KOJI_WEB_URL);
        assertThat(bc.getOutputDirectory()).isEqualTo(ConfigDefaults.OUTPUT_DIR);
        assertThat(bc.getPncNumThreads()).isEqualTo(ConfigDefaults.PNC_NUM_THREADS);
        assertThat(bc.getPncPartitionSize()).isEqualTo(ConfigDefaults.PNC_PARTITION_SIZE);
        assertThat(bc.getPncURL()).isEqualTo(ConfigDefaults.PNC_URL);
        assertThat(bc.getUseBuildsFile()).isEqualTo(ConfigDefaults.USE_BUILDS_FILE);
        assertThat(bc.getUseChecksumsFile()).isEqualTo(ConfigDefaults.USE_CHECKSUMS_FILE);
    }

    @Test
    void testMapping() throws IOException {
        String json = "{\"archive-types\":[\"jar\"]," + "\"excludes\":\"^(?!.*/pom\\\\.xml$).*/.*\\\\.xml$\","
                + "\"checksum-only\":true," + "\"checksum-type\":\"md5\"}";
        BuildConfig bc = BuildConfig.load(json);

        assertThat(bc.getArchiveTypes()).containsExactly("jar");
        assertThat(bc.getChecksumOnly()).isTrue();
        assertThat(bc.getChecksumTypes()).containsExactly(ChecksumType.md5);

        List<String> excludes = Collections
                .unmodifiableList(bc.getExcludes().stream().map(Pattern::pattern).collect(Collectors.toList()));

        assertThat(excludes).containsExactly(Pattern.compile("^(?!.*/pom\\.xml$).*/.*\\.xml$").pattern());
    }

    @Test
    void testMappingWithDefaults() throws IOException {
        String json = "{\"koji-hub-url\":\"https://my.url.com/hub\",\"koji-web-url\":\"https://my.url.com/web\"}";

        BuildConfig bc = BuildConfig.load(json);

        assertThat(bc.getKojiHubURL().toExternalForm()).isEqualTo("https://my.url.com/hub");
        assertThat(bc.getKojiWebURL().toExternalForm()).isEqualTo("https://my.url.com/web");
    }

    @Test
    void testIgnoreUnknownProperties() throws IOException {
        String json = "{\"foo\":\"bar\"}";

        BuildConfig bc = BuildConfig.load(json);

        assertThat(bc).isNotNull();
    }

    @Test
    void testSave(@TempDir File folder) throws IOException {
        String json = "{\"archive-types\":[\"jar\"]," + "\"excludes\":\"^(?!.*/pom\\\\.xml$).*/.*\\\\.xml$\","
                + "\"checksum-only\":true," + "\"checksum-type\":\"md5\"}";
        BuildConfig bc = BuildConfig.load(json);
        File file = new File(folder, "config.json");

        bc.save(file);

        assertThat(contentOf(file, StandardCharsets.UTF_8)).contains("  \"archive_types\" : [ \"jar\" ],");

        BuildConfig bc2 = BuildConfig.load(file);

        assertThat(bc2).hasToString(bc.toString());
    }

    @Test
    void testLoadFromClassPath() throws IOException {
        String json = "{\"archive-types\":[\"jar\"]," + "\"excludes\":\"^(?!.*/pom\\\\.xml$).*/.*\\\\.xml$\","
                + "\"checksum-only\":true," + "\"checksum-type\":\"sha256\","
                + "\"koji-hub-url\":\"https://my.url.com/hub\"," + "\"koji-web-url\":\"https://my.url.com/web\"}";
        BuildConfig bc = BuildConfig.load(Thread.currentThread().getContextClassLoader());

        assertThat(bc).isNotNull();
        assertThat(bc.getArchiveTypes()).containsExactly("jar", "xml", "pom", "so", "dll", "dylib");
        assertThat(bc.getArchiveExtensions()).containsExactly(
                "dll",
                "dylib",
                "ear",
                "jar",
                "jdocbook",
                "jdocbook-style",
                "kar",
                "plugin",
                "pom",
                "rar",
                "sar",
                "so",
                "war",
                "xml");
        assertThat(bc.getChecksumOnly()).isFalse();
        assertThat(bc.getChecksumTypes()).containsExactly(ChecksumType.md5);
        assertThat(bc.getKojiHubURL()).isNull();
        assertThat(bc.getKojiWebURL()).isNull();

        BuildConfig bc2 = BuildConfig.load(json);

        assertThat(bc2).isNotNull();
        assertThat(bc2.getArchiveExtensions()).containsExactly(
                "dll",
                "dylib",
                "ear",
                "jar",
                "jdocbook",
                "jdocbook-style",
                "kar",
                "plugin",
                "pom",
                "rar",
                "sar",
                "so",
                "war",
                "xml");
        assertThat(bc2.getArchiveTypes()).containsExactly("jar");
        assertThat(bc2.getChecksumOnly()).isTrue();
        assertThat(bc2.getChecksumTypes()).containsExactly(ChecksumType.sha256);
        assertThat(bc2.getKojiHubURL().toExternalForm()).isEqualTo("https://my.url.com/hub");
        assertThat(bc2.getKojiWebURL().toExternalForm()).isEqualTo("https://my.url.com/web");

        BuildConfig merged = BuildConfig.merge(bc, json);

        assertThat(merged.getArchiveExtensions()).containsExactly(
                "dll",
                "dylib",
                "ear",
                "jar",
                "jdocbook",
                "jdocbook-style",
                "kar",
                "plugin",
                "pom",
                "rar",
                "sar",
                "so",
                "war",
                "xml");
        assertThat(merged.getArchiveTypes()).containsExactly("jar");
        assertThat(merged.getChecksumOnly()).isTrue();
        assertThat(merged.getChecksumTypes()).containsExactly(ChecksumType.sha256);
        assertThat(merged.getKojiHubURL().toExternalForm()).isEqualTo("https://my.url.com/hub");
        assertThat(merged.getKojiWebURL().toExternalForm()).isEqualTo("https://my.url.com/web");
    }
}
