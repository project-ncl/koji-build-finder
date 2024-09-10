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

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.util.jar.Attributes.Name.MANIFEST_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.VFS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LicenseUtilsTest {
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
    }
}
