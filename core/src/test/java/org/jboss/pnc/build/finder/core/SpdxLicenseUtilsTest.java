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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.build.finder.core.SpdxLicenseUtils.NONE;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.VFS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.spdx.core.DefaultStoreNotInitializedException;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.library.LicenseInfoFactory;
import org.spdx.library.model.v2.license.InvalidLicenseStringException;
import org.spdx.library.model.v3_0_1.expandedlicensing.ListedLicense;
import org.spdx.library.model.v3_0_1.expandedlicensing.WithAdditionOperator;

class SpdxLicenseUtilsTest {
    @Test
    void testGetSPDXLicenseListVersion() {
        assertThat(SpdxLicenseUtils.getSPDXLicenseListVersion()).isNotEmpty();
    }

    @Test
    void testParseSPDXLicenseString() throws InvalidLicenseStringException, DefaultStoreNotInitializedException {
        assertThat(SpdxLicenseUtils.parseSPDXLicenseString("(GPL-2.0 WITH Universal-FOSS-exception-1.0)"))
                .isInstanceOf(WithAdditionOperator.class);
    }

    @Test
    void testFindMatchingLicenseId() {
        assertThat(SpdxLicenseUtils.findMatchingLicenseId("Apache License, Version 2.0")).isEmpty();
        assertThat(SpdxLicenseUtils.findMatchingLicenseId("Apache-2.0")).hasValue("Apache-2.0");
    }

    @Test
    void testFindMatchingLicenseName() {
        assertThat(SpdxLicenseUtils.findMatchingLicenseName("Apache License, Version 2.0", null))
                .hasValue("Apache-2.0");
        assertThat(SpdxLicenseUtils.findMatchingLicenseName("Apache-2.0", null)).hasValue("Apache-2.0");
        assertThat(SpdxLicenseUtils.findMatchingLicenseName("# Eclipse Public License - v 2.0", null))
                .hasValue("EPL-2.0");
        String s = "This software is dual-licensed under: - the Lesser General Public License (LGPL) version 3.0 or, at your option, any later version";
        assertThat(SpdxLicenseUtils.findMatchingLicenseName(s, null)).hasValue("LGPL-3.0-or-later");
        assertThat(SpdxLicenseUtils.findMatchingLicenseName("GNU Lesser General Public License v3.0 only", null))
                .hasValue("LGPL-3.0-only");
    }

    @Test
    void testGetSPDXLicenseId() {
        Map<String, List<String>> mapping = Map.of(NONE, List.of("Public Domain"));
        assertThat(SpdxLicenseUtils.getSPDXLicenseId(mapping, "  Public Domain\n", null)).isEqualTo(NONE);
        assertThat(SpdxLicenseUtils.getSPDXLicenseId(mapping, null, "http://repository.jboss.org/licenses/cc0-1.0.txt"))
                .isEqualTo("CC0-1.0");
        assertThat(
                SpdxLicenseUtils
                        .getSPDXLicenseId(mapping, "Public Domain", "http://repository.jboss.org/licenses/cc0-1.0.txt"))
                .isEqualTo("CC0-1.0");
    }

    @Test
    void testFindMatchingLicenseNameInText() {
        String s = "This software is dual-licensed under:  - the Lesser General Public License (LGPL) version 3.0 or, at your option, any later version;"
                + "- the Apache Software License (ASL) version 2.0.";
        assertThat(SpdxLicenseUtils.findMatchingLicenseName(s, null)).hasValue("LGPL-3.0-or-later");
        s = "GNU LESSER GENERAL PUBLIC LICENSE Version 2.1, February 1999";
        assertThat(SpdxLicenseUtils.findMatchingLicenseName(s, null)).hasValue("LGPL-2.1-only");
        s = "The GNU General Public License (GPL) Version 2, June 1991";
        assertThat(SpdxLicenseUtils.findMatchingLicenseName(s, null)).hasValue("GPL-2.0-only");
    }

    @Test
    void testFindMatchingLicenseSeeAlso() {
        assertThat(SpdxLicenseUtils.findMatchingLicenseSeeAlso("https://www.apache.org/licenses/LICENSE-2.0"))
                .hasValue("Apache-2.0");
        assertThat(SpdxLicenseUtils.findMatchingLicenseSeeAlso("https://www.apache.org/licenses/LICENSE-2.0.html"))
                .hasValue("Apache-2.0");
        assertThat(SpdxLicenseUtils.findMatchingLicenseSeeAlso("https://www.apache.org/licenses/LICENSE-2.0.txt"))
                .hasValue("Apache-2.0");
        assertThat(SpdxLicenseUtils.findMatchingLicenseSeeAlso("https://apache.org/licenses/LICENSE-2.0.html"))
                .hasValue("Apache-2.0");
    }

    @Test
    void testGetNumberOfSPDXLicenses() {
        assertThat(SpdxLicenseUtils.getNumberOfSPDXLicenses()).isPositive();
    }

    @Test
    void testFindLicenseMapping() {
        assertThat(SpdxLicenseUtils.findLicenseMapping("Apache License")).hasValue("Apache-2.0");
        assertThat(SpdxLicenseUtils.findLicenseMapping("https://www.freemarker.org/LICENSE.txt"))
                .hasValue("Apache-2.0");
        assertThat(SpdxLicenseUtils.findLicenseMapping("http://repository.jboss.org/licenses/lgpl-2.1.txt"))
                .hasValue("LGPL-2.1-only");
        assertThat(SpdxLicenseUtils.findLicenseMapping("http://www.gnu.org/licenses/lgpl-2.1.txt"))
                .hasValue("LGPL-2.1-only");
        assertThat(SpdxLicenseUtils.findLicenseMapping("https://projects.eclipse.org/license/secondary-gpl-2.0-cp"))
                .hasValue("GPL-2.0-only WITH Classpath-exception-2.0");
        assertThat(SpdxLicenseUtils.findLicenseMapping("http://www.jcraft.com/jzlib/LICENSE.txt"))
                .hasValue("BSD-3-Clause");
        assertThat(SpdxLicenseUtils.findLicenseMapping("see: http://freemarker.org/LICENSE.txt"))
                .hasValue("Apache-2.0");
    }

    @Test
    void testFindMatchingLicense() {
        assertThat(SpdxLicenseUtils.findMatchingLicense("Apache License 2.0", "https://www.freemarker.org/LICENSE.txt"))
                .hasValue("Apache-2.0");
        assertThat(SpdxLicenseUtils.findMatchingLicense("AL2", "https://repository.jboss.org/licenses/apache-2.0.txt"))
                .hasValue("Apache-2.0");
        assertThat(
                SpdxLicenseUtils
                        .findMatchingLicense(null, "https://projects.eclipse.org/content/eclipse-public-license-1.0"))
                .hasValue("EPL-1.0");
        assertThat(SpdxLicenseUtils.findMatchingLicense(null, "http://www.opensource.org/licenses/cpl1.0.txt"))
                .hasValue("CPL-1.0");
        assertThat(SpdxLicenseUtils.findMatchingLicense(null, "http://www.eclipse.org/legal/epl-v20.html"))
                .hasValue("EPL-2.0");
        assertThat(SpdxLicenseUtils.findMatchingLicense(null, "http://www.opensource.org/licenses/apache2.0.php"))
                .hasValue("Apache-2.0");
        assertThat(SpdxLicenseUtils.findMatchingLicense(null, "http://creativecommons.org/licenses/by/2.5/"))
                .hasValue("CC-BY-2.5");
        assertThat(SpdxLicenseUtils.findMatchingLicense(null, "http://repository.jboss.org/licenses/cc0-1.0.txt"))
                .hasValue("CC0-1.0");
    }

    // TODO: No support for SPDX license expressions, so returns only first license for now
    @Test
    void testFindMatchingSPDXLicenseIdentifier() {
        String s = "// SPDX-License-Identifier: Apache-2.0 OR LGPL-2.1";
        assertThat(SpdxLicenseUtils.findMatchingSPDXLicenseIdentifier(s)).hasValue("Apache-2.0");
    }

    @Test
    void testGetMatchingLicense(@TempDir Path folder) throws IOException, InvalidSPDXAnalysisException {
        URL url = new URL("https://www.apache.org/licenses/LICENSE-2.0.txt");
        Path path = folder.resolve("LICENSE-2.0-1.txt");

        try (InputStream in = url.openStream()) {
            Files.copy(in, path);
        }

        String s1 = Files.readString(path);
        String s2 = s1.replace("http://www.apache.org/licenses/", "https://www.apache.org/licenses/");
        Path path2 = folder.resolve("LICENSE-2.0-2.txt");
        Files.writeString(path2, s2);

        String http = """
                                                 Apache License
                                           Version 2.0, January 2004
                                        http://www.apache.org/licenses/

                """;
        String https = """
                                                 Apache License
                                           Version 2.0, January 2004
                                        https://www.apache.org/licenses/

                """;

        assertThat(LicenseUtils.licenseFileToText(path)).isEqualToIgnoringWhitespace(http);
        assertThat(LicenseUtils.licenseFileToText(path2)).isEqualToIgnoringWhitespace(https);

        ListedLicense listedLicense = LicenseInfoFactory.getListedLicenseById("Apache-2.0");
        assertThat(SpdxLicenseUtils.findMatchingSPDXLicenseIdentifier(listedLicense, s1)).hasValue("Apache-2.0");
        assertThat(SpdxLicenseUtils.findMatchingSPDXLicenseIdentifier(listedLicense, s2)).hasValue("Apache-2.0");

        assertThat(SpdxLicenseUtils.findMatchingSPDXLicenseIdentifierOrLicense(s1)).hasValue("Apache-2.0");
        assertThat(SpdxLicenseUtils.findMatchingSPDXLicenseIdentifierOrLicense(s2)).hasValue("Apache-2.0");

        try (FileObject fo = VFS.getManager().resolveFile(path.toUri())) {
            try (FileContent fc = fo.getContent(); InputStream in = fc.getInputStream()) {
                String s = new String(in.readAllBytes(), UTF_8);
                assertThat(s).isEqualTo(s1);
            }

            assertThat(SpdxLicenseUtils.getMatchingLicense(fo)).isEqualTo("Apache-2.0");
        }

        try (FileObject fo = VFS.getManager().resolveFile(path2.toUri())) {
            try (FileContent fc = fo.getContent(); InputStream in = fc.getInputStream()) {
                String s = new String(in.readAllBytes(), UTF_8);
                assertThat(s).isEqualTo(s2);
            }

            assertThat(SpdxLicenseUtils.getMatchingLicense(fo)).isEqualTo("Apache-2.0");
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                    "LICENSE",
                    "LICENSE.md",
                    "LICENSE.txt",
                    "LICENSE-2.0.txt",
                    "MIT-LICENSE",
                    "MIT",
                    "MIT.md",
                    "MIT.txt",
                    "META-INF/LGPL-3.0.txt" })
    void testIsLicenseFileName(String fileName) {
        assertThat(SpdxLicenseUtils.isLicenseFileName(fileName)).isTrue();
    }

    @Test
    void testIsLicenseFileName2() {
        assertThat(SpdxLicenseUtils.isLicenseFileName("org/dom4j/xpp")).isTrue();
    }

    @Test
    void testGetSPDXLicenseName() {
        assertThat(SpdxLicenseUtils.getSPDXLicenseName("Apache-2.0")).isEqualTo("Apache License 2.0");
    }
}
