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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.io.File;
import java.io.IOException;
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
    void verifyDefaults() throws JsonProcessingException {
        BuildConfig bc = new BuildConfig();
        String s = JSONUtils.dumpString(bc);

        assertThat(s, is(not(nullValue())));

        LOGGER.debug("Default configuration: {}", s);

        assertThat(bc.getArchiveTypes(), is(ConfigDefaults.ARCHIVE_TYPES));
        assertThat(bc.getArchiveExtensions(), is(ConfigDefaults.ARCHIVE_EXTENSIONS));
        assertThat(bc.getBuildSystems(), is(ConfigDefaults.BUILD_SYSTEMS));
        assertThat(bc.getCacheLifespan(), is(ConfigDefaults.CACHE_LIFESPAN));
        assertThat(bc.getCacheMaxIdle(), is(ConfigDefaults.CACHE_MAX_IDLE));
        assertThat(bc.getChecksumOnly(), is(ConfigDefaults.CHECKSUM_ONLY));
        assertThat(bc.getChecksumTypes(), is(ConfigDefaults.CHECKSUM_TYPES));
        assertThat(bc.getDisableCache(), is(ConfigDefaults.DISABLE_CACHE));
        assertThat(bc.getDisableRecursion(), is(ConfigDefaults.DISABLE_RECURSION));
        assertThat(bc.getExcludes(), is(ConfigDefaults.EXCLUDES));
        assertThat(bc.getKojiHubURL(), is(ConfigDefaults.KOJI_HUB_URL));
        assertThat(bc.getKojiMulticallSize(), is(ConfigDefaults.KOJI_MULTICALL_SIZE));
        assertThat(bc.getKojiNumThreads(), is(ConfigDefaults.KOJI_NUM_THREADS));
        assertThat(bc.getKojiWebURL(), is(ConfigDefaults.KOJI_WEB_URL));
        assertThat(bc.getOutputDirectory(), is(ConfigDefaults.OUTPUT_DIR));
        assertThat(bc.getPncPartitionSize(), is(ConfigDefaults.PNC_PARTITION_SIZE));
        assertThat(bc.getPncURL(), is(ConfigDefaults.PNC_URL));
        assertThat(bc.getUseBuildsFile(), is(ConfigDefaults.USE_BUILDS_FILE));
        assertThat(bc.getUseChecksumsFile(), is(ConfigDefaults.USE_CHECKSUMS_FILE));
    }

    @Test
    void verifyMapping() throws IOException {
        String json = "{\"archive-types\":[\"jar\"]," + "\"excludes\":\"^(?!.*/pom\\\\.xml$).*/.*\\\\.xml$\","
                + "\"checksum-only\":true," + "\"checksum-type\":\"md5\"}";
        BuildConfig bc = BuildConfig.load(json);

        assertThat(bc.getArchiveTypes(), contains("jar"));
        assertThat(bc.getChecksumOnly(), is(true));
        assertThat(bc.getChecksumTypes(), contains(ChecksumType.md5));

        List<String> excludes = Collections
                .unmodifiableList(bc.getExcludes().stream().map(Pattern::pattern).collect(Collectors.toList()));

        assertThat(excludes, contains(Pattern.compile("^(?!.*/pom\\.xml$).*/.*\\.xml$").pattern()));
    }

    @Test
    void verifyMappingWithDefaults() throws IOException {
        String json = "{\"koji-hub-url\":\"https://my.url.com/hub\",\"koji-web-url\":\"https://my.url.com/web\"}";

        BuildConfig bc = BuildConfig.load(json);

        assertThat(bc.getKojiHubURL().toExternalForm(), is("https://my.url.com/hub"));
        assertThat(bc.getKojiWebURL().toExternalForm(), is("https://my.url.com/web"));
    }

    @Test
    void verifyIgnoreUnknownProperties() throws IOException {
        String json = "{\"foo\":\"bar\"}";

        BuildConfig bc = BuildConfig.load(json);

        assertThat(bc, is(not(nullValue())));
    }

    @Test
    void verifySave(@TempDir File folder) throws IOException {
        String json = "{\"archive-types\":[\"jar\"]," + "\"excludes\":\"^(?!.*/pom\\\\.xml$).*/.*\\\\.xml$\","
                + "\"checksum-only\":true," + "\"checksum-type\":\"md5\"}";
        BuildConfig bc = BuildConfig.load(json);
        File file = new File(folder, "config.json");

        bc.save(file);

        BuildConfig bc2 = BuildConfig.load(file);

        assertThat(bc2.toString(), is(bc.toString()));
    }

    @Test
    void verifyLoadFromClassPath() throws IOException {
        String json = "{\"archive-types\":[\"jar\"]," + "\"excludes\":\"^(?!.*/pom\\\\.xml$).*/.*\\\\.xml$\","
                + "\"checksum-only\":true," + "\"checksum-type\":\"sha256\","
                + "\"koji-hub-url\":\"https://my.url.com/hub\"," + "\"koji-web-url\":\"https://my.url.com/web\"}";
        BuildConfig bc = BuildConfig.load(BuildConfigTest.class.getClassLoader());

        assertThat(bc, is(not(nullValue())));
        assertThat(bc.getArchiveTypes(), contains("jar", "xml", "pom", "so", "dll", "dylib"));
        assertThat(
                bc.getArchiveExtensions(),
                contains(
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
                        "xml"));
        assertThat(bc.getChecksumOnly(), is(false));
        assertThat(bc.getChecksumTypes(), contains(ChecksumType.md5));
        assertThat(bc.getKojiHubURL(), is(nullValue()));
        assertThat(bc.getKojiWebURL(), is(nullValue()));

        BuildConfig bc2 = BuildConfig.load(json);

        assertThat(bc2, is(not(nullValue())));
        assertThat(
                bc2.getArchiveExtensions(),
                contains(
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
                        "xml"));
        assertThat(bc2.getArchiveTypes(), contains("jar"));
        assertThat(bc2.getChecksumOnly(), is(true));
        assertThat(bc2.getChecksumTypes(), contains(ChecksumType.sha256));
        assertThat(bc2.getKojiHubURL().toExternalForm(), is("https://my.url.com/hub"));
        assertThat(bc2.getKojiWebURL().toExternalForm(), is("https://my.url.com/web"));

        BuildConfig merged = BuildConfig.merge(bc, json);

        assertThat(
                merged.getArchiveExtensions(),
                contains(
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
                        "xml"));
        assertThat(merged.getArchiveTypes(), contains("jar"));
        assertThat(merged.getChecksumOnly(), is(true));
        assertThat(merged.getChecksumTypes(), contains(ChecksumType.sha256));
        assertThat(merged.getKojiHubURL().toExternalForm(), is("https://my.url.com/hub"));
        assertThat(merged.getKojiWebURL().toExternalForm(), is("https://my.url.com/web"));
    }
}
