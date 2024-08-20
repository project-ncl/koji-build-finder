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

import static com.google.common.base.CaseFormat.LOWER_HYPHEN;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.google.common.io.Resources;

@WireMockTest
abstract class AbstractWireMockTest {
    static WireMockExtension newWireMockExtensionForClass(Class<?> clazz) {
        String path = getDirectoryForClass(clazz);
        Options options = WireMockConfiguration.wireMockConfig().dynamicPort().usingFilesUnderDirectory(path);
        return WireMockExtension.newInstance().options(options).build();
    }

    private static String getDirectoryForClass(Class<?> clazz) {
        String resourceName = UPPER_CAMEL.to(LOWER_HYPHEN, clazz.getSimpleName());

        try {
            Path directory = Path.of(Resources.getResource(resourceName).toURI());
            assertThat(directory).hasFileName(resourceName).isNotEmptyDirectory();
            return directory.toAbsolutePath().toString();
        } catch (URISyntaxException e) {
            fail(e);
        }

        fail("unreachable");
        return null;
    }

    abstract Map<Checksum, Collection<String>> getChecksumTable();
}
