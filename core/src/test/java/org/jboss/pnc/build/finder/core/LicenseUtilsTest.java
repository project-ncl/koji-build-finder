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
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.util.jar.Attributes.Name.MANIFEST_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.jboss.pnc.build.finder.core.LicenseUtils.NONE;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.VFS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.model.license.InvalidLicenseStringException;
import org.spdx.library.model.license.LicenseInfoFactory;
import org.spdx.library.model.license.SpdxListedLicense;
import org.spdx.library.model.license.WithExceptionOperator;

class LicenseUtilsTest {
    @BeforeAll
    static void setup() throws IOException {
        Map<String, List<String>> mapping = LicenseUtils.loadSpdxLicenses();
        assertThat(mapping).isNotEmpty();
    }

    @Test
    void testNormalizeLicenseUrl() {
        assertThat(LicenseUtils.normalizeLicenseUrl("https://www.opensource.org/licenses/cddl1.php"))
                .isEqualTo("opensource-org/licenses/cddl-1");
        assertThat(LicenseUtils.normalizeLicenseUrl("https://creativecommons.org/publicdomain/zero/1.0/"))
                .isEqualTo("cc-org/publicdomain/zero/1.0");
    }

    @Test
    void tesContainsWordsInSameOrder() {
        String name = "Apache License, Version 2.0";
        String id = "Apache-2.0";
        assertThat(LicenseUtils.containsWordsInSameOrder(name, id)).isTrue();
    }

    // FIXME: Number format should probably be "1.0" instead of "10" or "1 0", but this works well enough for now
    static Stream<Arguments> stringListStringProvider() {
        return Stream.of(
                arguments(
                        "https://repository.jboss.org/licenses/apache-2.0.txt",
                        List.of("apache", "20"),
                        "Apache-2.0"),
                arguments("https://repository.jboss.org/licenses/cc0-1.0.txt", List.of("cc0", "10"), "CC0-1.0"),
                arguments("https://www.eclipse.org/legal/epl-2.0/", List.of("epl", "20"), "EPL-2.0"),
                arguments("http://www.eclipse.org/org/documents/epl-v10.php", List.of("epl", "10"), "EPL-1.0"),
                arguments("http://www.eclipse.org/legal/epl-v20.html", List.of("epl", "20"), "EPL-2.0"),
                arguments("http://www.opensource.org/licenses/cpl1.0.txt", List.of("cpl", "10"), "CPL-1.0"),
                arguments("http://oss.oracle.com/licenses/upl", List.of("upl"), "UPL"));
    }

    @ParameterizedTest
    @MethodSource("stringListStringProvider")
    void tesContainsWordsInSameOrderUrl1(String licenseUrl, List<String> tokens, String licenseId) {
        List<String> tokens1 = LicenseUtils.tokenizeLicenseString(licenseUrl);
        assertThat(tokens1).containsAll(tokens);
        assertThat(LicenseUtils.containsWordsInSameOrder(licenseUrl, licenseId)).isTrue();
    }

    @Test
    void testFindMatchingLicenseId() {
        assertThat(LicenseUtils.findMatchingLicenseId("Apache License, Version 2.0")).isEmpty();
        assertThat(LicenseUtils.findMatchingLicenseId("Apache-2.0")).hasValue("Apache-2.0");
    }

    @Test
    void testFindMatchingLicenseName() {
        assertThat(LicenseUtils.findMatchingLicenseName("Apache License, Version 2.0", null)).hasValue("Apache-2.0");
        assertThat(LicenseUtils.findMatchingLicenseName("Apache-2.0", null)).hasValue("Apache-2.0");
        assertThat(LicenseUtils.findMatchingLicenseName("# Eclipse Public License - v 2.0", null)).hasValue("EPL-2.0");
        String s = "This software is dual-licensed under: - the Lesser General Public License (LGPL) version 3.0 or, at your option, any later version";
        assertThat(LicenseUtils.findMatchingLicenseName(s, null)).hasValue("LGPL-3.0-or-later");
        assertThat(LicenseUtils.findMatchingLicenseName("GNU Lesser General Public License v3.0 only", null))
                .hasValue("LGPL-3.0-only");
    }

    @Test
    void testGetSPDXLicenseId() {
        Map<String, List<String>> mapping = Map.of(NONE, List.of("Public Domain"));
        assertThat(LicenseUtils.getSPDXLicenseId(mapping, "  Public Domain\n", null)).isEqualTo(NONE);
        assertThat(LicenseUtils.getSPDXLicenseId(mapping, null, "http://repository.jboss.org/licenses/cc0-1.0.txt"))
                .isEqualTo("CC0-1.0");
        assertThat(
                LicenseUtils
                        .getSPDXLicenseId(mapping, "Public Domain", "http://repository.jboss.org/licenses/cc0-1.0.txt"))
                .isEqualTo("CC0-1.0");
    }

    @Test
    void testFindMatchingLicenseNameInText() {
        String s = "This software is dual-licensed under:  - the Lesser General Public License (LGPL) version 3.0 or, at your option, any later version;"
                + "- the Apache Software License (ASL) version 2.0.";
        assertThat(LicenseUtils.findMatchingLicenseName(s, null)).hasValue("LGPL-3.0-or-later");
        s = "GNU LESSER GENERAL PUBLIC LICENSE Version 2.1, February 1999";
        assertThat(LicenseUtils.findMatchingLicenseName(s, null)).hasValue("LGPL-2.1-only");
        s = "The GNU General Public License (GPL) Version 2, June 1991";
        assertThat(LicenseUtils.findMatchingLicenseName(s, null)).hasValue("GPL-2.0-only");
    }

    @Test
    void testFindMatchingLicenseSeeAlso() {
        assertThat(LicenseUtils.findMatchingLicenseSeeAlso("https://www.apache.org/licenses/LICENSE-2.0"))
                .hasValue("Apache-2.0");
        assertThat(LicenseUtils.findMatchingLicenseSeeAlso("https://www.apache.org/licenses/LICENSE-2.0.html"))
                .hasValue("Apache-2.0");
        assertThat(LicenseUtils.findMatchingLicenseSeeAlso("https://www.apache.org/licenses/LICENSE-2.0.txt"))
                .hasValue("Apache-2.0");
        assertThat(LicenseUtils.findMatchingLicenseSeeAlso("https://apache.org/licenses/LICENSE-2.0.html"))
                .hasValue("Apache-2.0");
    }

    @Test
    void testGetNumberOfSPDXLicenses() {
        assertThat(LicenseUtils.getNumberOfSPDXLicenses()).isPositive();
    }

    @Test
    void testGetSPDXLicenseListVersion() {
        assertThat(LicenseUtils.getSPDXLicenseListVersion()).isNotEmpty();
    }

    @Test
    void testParseSPDXLicenseString() throws InvalidLicenseStringException {
        assertThat(LicenseUtils.parseSPDXLicenseString("(GPL-2.0 WITH Universal-FOSS-exception-1.0)"))
                .isInstanceOf(WithExceptionOperator.class);
    }

    @Test
    void testFindLicenseMapping() {
        assertThat(LicenseUtils.findLicenseMapping("Apache License")).hasValue("Apache-2.0");
        assertThat(LicenseUtils.findLicenseMapping("https://www.freemarker.org/LICENSE.txt")).hasValue("Apache-2.0");
        assertThat(LicenseUtils.findLicenseMapping("http://repository.jboss.org/licenses/lgpl-2.1.txt"))
                .hasValue("LGPL-2.1-only");
        assertThat(LicenseUtils.findLicenseMapping("http://www.gnu.org/licenses/lgpl-2.1.txt"))
                .hasValue("LGPL-2.1-only");
        assertThat(LicenseUtils.findLicenseMapping("https://projects.eclipse.org/license/secondary-gpl-2.0-cp"))
                .hasValue("GPL-2.0-only WITH Classpath-exception-2.0");
        assertThat(LicenseUtils.findLicenseMapping("http://www.jcraft.com/jzlib/LICENSE.txt")).hasValue("BSD-3-Clause");
        assertThat(LicenseUtils.findLicenseMapping("see: http://freemarker.org/LICENSE.txt")).hasValue("Apache-2.0");
    }

    @Test
    void testFindMatchingLicense() {
        assertThat(LicenseUtils.findMatchingLicense("Apache License 2.0", "https://www.freemarker.org/LICENSE.txt"))
                .hasValue("Apache-2.0");
        assertThat(LicenseUtils.findMatchingLicense("AL2", "https://repository.jboss.org/licenses/apache-2.0.txt"))
                .hasValue("Apache-2.0");
        assertThat(
                LicenseUtils
                        .findMatchingLicense(null, "https://projects.eclipse.org/content/eclipse-public-license-1.0"))
                .hasValue("EPL-1.0");
        assertThat(LicenseUtils.findMatchingLicense(null, "http://www.opensource.org/licenses/cpl1.0.txt"))
                .hasValue("CPL-1.0");
        assertThat(LicenseUtils.findMatchingLicense(null, "http://www.eclipse.org/legal/epl-v20.html"))
                .hasValue("EPL-2.0");
        assertThat(LicenseUtils.findMatchingLicense(null, "http://www.opensource.org/licenses/apache2.0.php"))
                .hasValue("Apache-2.0");
        assertThat(LicenseUtils.findMatchingLicense(null, "http://creativecommons.org/licenses/by/2.5/"))
                .hasValue("CC-BY-2.5");
        assertThat(LicenseUtils.findMatchingLicense(null, "http://repository.jboss.org/licenses/cc0-1.0.txt"))
                .hasValue("CC0-1.0");
    }

    @Test
    void testGetBundleLicenseFromManifest() throws IOException {
        String actual = "\"Apache-2.0\";link=\"https://www.apache.org/licenses/LICENSE-2.0.txt\"";
        List<BundleLicense> bundleLicenses = LicenseUtils.getBundleLicenseFromManifest(actual);
        assertThat(bundleLicenses).hasSize(1)
                .element(0)
                .extracting("licenseIdentifier", "link")
                .containsExactly("Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0.txt");
        actual = "Apache-2.0; link=\"https://www.apache.org/licenses/LICENSE-2.0\"; description=\"Apache License, Version 2.0\"";
        bundleLicenses = LicenseUtils.getBundleLicenseFromManifest(actual);
        assertThat(bundleLicenses).hasSize(1)
                .element(0)
                .extracting("licenseIdentifier", "link", "description")
                .containsExactly(
                        "Apache-2.0",
                        "https://www.apache.org/licenses/LICENSE-2.0",
                        "Apache License, Version 2.0");
        actual = "http://www.eclipse.org/legal/epl-2.0," + " https://www.gnu.org/software/classpath/license.html,"
                + " http://www.eclipse.org/org/documents/edl-v10.php";
        bundleLicenses = LicenseUtils.getBundleLicenseFromManifest(actual);
        assertThat(bundleLicenses).hasSize(3)
                .flatMap(BundleLicense::getLink)
                .containsExactly(
                        "http://www.eclipse.org/legal/epl-2.0",
                        "https://www.gnu.org/software/classpath/license.html",
                        "http://www.eclipse.org/org/documents/edl-v10.php");
        actual = "MIT";
        bundleLicenses = LicenseUtils.getBundleLicenseFromManifest(actual);
        assertThat(bundleLicenses).hasSize(1).flatMap(BundleLicense::getLicenseIdentifier).containsExactly("MIT");
        actual = "LICENSE.txt";
        bundleLicenses = LicenseUtils.getBundleLicenseFromManifest(actual);
        assertThat(bundleLicenses).hasSize(1)
                .flatMap(BundleLicense::getLicenseIdentifier)
                .containsExactly("LICENSE.txt");
    }

    @Test
    void testGetBundleLicenseFromManifestThrowsException() {
        String s = "Apache License, Version 2.0; see: http://www.apache.org/\n" + " licenses/LICENSE-2.0.txt";
        assertThatCode(() -> LicenseUtils.getBundleLicenseFromManifest(s)).isInstanceOf(IOException.class)
                .hasMessage(
                        "Expected key=value pair, but got see: http://www.apache.org/\n" + " licenses/LICENSE-2.0.txt");
    }

    @Test
    void testGetBundleLicenseFromManifestFile(@TempDir Path tempDir) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Bundle-License", "http://repository.jboss.org/licenses/cc0-1.0.txt");
        Path metaInfPath = tempDir.resolve("META-INF");
        Files.createDirectory(metaInfPath);
        Path manifestMfPath = metaInfPath.resolve("MANIFEST.MF");

        try (OutputStream outputStream = Files.newOutputStream(manifestMfPath, CREATE_NEW)) {
            manifest.write(outputStream);
        }

        FileObject fo = VFS.getManager().resolveFile(manifestMfPath.toUri());
        List<BundleLicense> bundlesLicenses = LicenseUtils.getBundleLicenseFromManifest(fo);
        assertThat(bundlesLicenses).hasSize(1);
        BundleLicense bundleLicense = bundlesLicenses.get(0);
        assertThat(bundleLicense).extracting("licenseIdentifier").isNull();
        assertThat(bundleLicense).extracting("link").isEqualTo("http://repository.jboss.org/licenses/cc0-1.0.txt");
        assertThat(LicenseUtils.getSPDXLicenseId(Map.of(), null, bundleLicense.getLink())).isEqualTo("CC0-1.0");
    }

    // TODO: No support for SPDX license expressions, so returns only first license for now
    @Test
    void testFindMatchingSPDXLicenseIdentifier() {
        String s = "// SPDX-License-Identifier: Apache-2.0 OR LGPL-2.1";
        assertThat(LicenseUtils.findMatchingSPDXLicenseIdentifier(s)).hasValue("Apache-2.0");
    }

    @Test
    void testGetMatchingLicense() throws IOException, InvalidSPDXAnalysisException {
        Path path = Files.createTempFile("file1-", null);
        URL url = new URL("https://www.apache.org/licenses/LICENSE-2.0.txt");
        FileUtils.copyURLToFile(url, path.toFile());
        String s1 = Files.readString(path);
        String s2 = s1.replace("http://www.apache.org/licenses/", "https://www.apache.org/licenses/");
        Path path2 = Files.createTempFile("file2-", null);
        Files.writeString(path2, s2);

        String http = "                                 Apache License\n"
                + "                           Version 2.0, January 2004\n"
                + "                        http://www.apache.org/licenses/\n" + "\n";
        String https = "                                 Apache License\n"
                + "                           Version 2.0, January 2004\n"
                + "                        https://www.apache.org/licenses/\n" + "\n";

        assertThat(LicenseUtils.licenseFileToText(path)).isEqualToIgnoringWhitespace(http);
        assertThat(LicenseUtils.licenseFileToText(path2)).isEqualToIgnoringWhitespace(https);

        SpdxListedLicense spdxListedLicense = LicenseInfoFactory.getListedLicenseById("Apache-2.0");
        assertThat(LicenseUtils.findMatchingSPDXLicenseIdentifier(spdxListedLicense, s1)).hasValue("Apache-2.0");
        assertThat(LicenseUtils.findMatchingSPDXLicenseIdentifier(spdxListedLicense, s2)).hasValue("Apache-2.0");

        assertThat(LicenseUtils.findMatchingSPDXLicenseIdentifierOrLicense(s1)).hasValue("Apache-2.0");
        assertThat(LicenseUtils.findMatchingSPDXLicenseIdentifierOrLicense(s2)).hasValue("Apache-2.0");

        try (FileObject fo = VFS.getManager().resolveFile(path.toUri())) {
            try (FileContent fc = fo.getContent(); InputStream in = fc.getInputStream()) {
                String s = new String(in.readAllBytes(), UTF_8);
                assertThat(s).isEqualTo(s1);
            }

            assertThat(LicenseUtils.getMatchingLicense(fo)).isEqualTo("Apache-2.0");
        }

        try (FileObject fo = VFS.getManager().resolveFile(path2.toUri())) {
            try (FileContent fc = fo.getContent(); InputStream in = fc.getInputStream()) {
                String s = new String(in.readAllBytes(), UTF_8);
                assertThat(s).isEqualTo(s2);
            }

            assertThat(LicenseUtils.getMatchingLicense(fo)).isEqualTo("Apache-2.0");
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
        assertThat(LicenseUtils.isLicenseFileName(fileName)).isTrue();
    }

    @Test
    void testIsLicenseFileName2() {
        assertThat(LicenseUtils.isLicenseFileName("org/dom4j/xpp")).isTrue();
    }

    @Test
    void testGetSPDXLicenseName() {
        assertThat(LicenseUtils.getSPDXLicenseName("Apache-2.0")).isEqualTo("Apache License 2.0");
    }
}
