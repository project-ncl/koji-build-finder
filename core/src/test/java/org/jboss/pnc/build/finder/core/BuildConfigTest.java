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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.ClearSystemProperty;
import org.junitpioneer.jupiter.SetSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

class BuildConfigTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildConfigTest.class);

    private static void userHome() {
        String userHome = Utils.getUserHome();

        LOGGER.debug("user.home={}", userHome);
    }

    @SetSystemProperty(key = "user.home", value = "?")
    @Test
    void verifyUserHomeQuestionMark() {
        assertThrows(RuntimeException.class, BuildConfigTest::userHome);
    }

    @ClearSystemProperty(key = "user.home")
    @Test
    void verifyUserHomeNull() {
        assertThrows(RuntimeException.class, BuildConfigTest::userHome);
    }

    @Test
    void verifyUserHome() {
        assertDoesNotThrow(BuildConfigTest::userHome);
    }

    @Test
    void verifyDefaults() throws JsonProcessingException {
        BuildConfig bc = new BuildConfig();

        String s = JSONUtils.dumpString(bc);

        LOGGER.debug("Default configuration:\n{}", s);

        assertNotNull(s);

        assertEquals(ConfigDefaults.ARCHIVE_TYPES, bc.getArchiveTypes());
        assertEquals(ConfigDefaults.CHECKSUM_ONLY, bc.getChecksumOnly());
        assertEquals(ConfigDefaults.CHECKSUM_TYPES, bc.getChecksumTypes());
        assertEquals(ConfigDefaults.EXCLUDES, bc.getExcludes());
        assertEquals(ConfigDefaults.KOJI_HUB_URL, bc.getKojiHubURL());
        assertEquals(ConfigDefaults.KOJI_WEB_URL, bc.getKojiWebURL());
    }

    @Test
    void verifyMapping() throws IOException {
        String json = "{\"archive-types\":[\"jar\"]," + "\"excludes\":\"^(?!.*/pom\\\\.xml$).*/.*\\\\.xml$\","
                + "\"checksum-only\":true," + "\"checksum-type\":\"md5\"}";
        BuildConfig bc = BuildConfig.load(json);

        assertTrue(bc.getChecksumOnly());
        assertTrue(bc.getArchiveTypes().size() == 1 && bc.getArchiveTypes().get(0).equals("jar"));
    }

    @Test
    void verifyMappingWithDefaults() throws IOException {
        String json = "{\"koji-hub-url\":\"https://my.url.com/hub\",\"koji-web-url\":\"https://my.url.com/web\"}";

        BuildConfig bc = BuildConfig.load(json);

        assertFalse(bc.getChecksumOnly());
        assertEquals("https://my.url.com/hub", bc.getKojiHubURL().toExternalForm());
        assertEquals("https://my.url.com/web", bc.getKojiWebURL().toExternalForm());
    }

    @Test
    void verifyIgnoreUnknownProperties() throws IOException {
        String json = "{\"foo\":\"bar\"}";

        BuildConfig bc = BuildConfig.load(json);

        assertNotNull(bc);
    }

    @Test
    void verifySave(@TempDir File folder) throws IOException {
        String json = "{\"archive-types\":[\"jar\"]," + "\"excludes\":\"^(?!.*/pom\\\\.xml$).*/.*\\\\.xml$\","
                + "\"checksum-only\":true," + "\"checksum-type\":\"md5\"}";
        BuildConfig bc = BuildConfig.load(json);
        File file = new File(folder, "config.json");

        bc.save(file);

        BuildConfig bc2 = BuildConfig.load(file);

        assertEquals(bc.toString(), bc2.toString());
    }

    @Test
    void verifyLoadFromClassPath() throws IOException {
        String json = "{\"archive-types\":[\"jar\"]," + "\"excludes\":\"^(?!.*/pom\\\\.xml$).*/.*\\\\.xml$\","
                + "\"checksum-only\":true," + "\"checksum-type\":\"sha256\","
                + "\"koji-hub-url\":\"https://my.url.com/hub\"," + "\"koji-web-url\":\"https://my.url.com/web\"}";
        BuildConfig bc = BuildConfig.load(BuildConfigTest.class.getClassLoader());
        BuildConfig bc2 = BuildConfig.load(json);

        assertNotNull(bc);
        assertNotNull(bc2);

        assertFalse(bc.getChecksumOnly());
        assertTrue(bc2.getChecksumOnly());

        BuildConfig merged = BuildConfig.merge(bc, json);

        assertTrue(merged.getChecksumOnly());
        assertTrue(merged.getArchiveTypes().size() == 1 && merged.getArchiveTypes().get(0).equals("jar"));
        assertTrue(merged.getArchiveExtensions().size() == 14 && merged.getArchiveExtensions().get(0).equals("dll"));
        assertEquals(ChecksumType.sha256, merged.getChecksumTypes().iterator().next());
        assertEquals("https://my.url.com/hub", bc.getKojiHubURL().toExternalForm());
        assertEquals("https://my.url.com/web", bc.getKojiWebURL().toExternalForm());
    }
}
