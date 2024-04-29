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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.spdx.library.model.license.InvalidLicenseStringException;
import org.spdx.library.model.license.WithExceptionOperator;

class LicenseUtilsTest {
    private static Map<String, List<String>> MAPPING;

    @BeforeAll
    static void setup() throws IOException {
        MAPPING = LicenseUtils.loadLicenseMapping();
        assertThat(MAPPING).isNotEmpty();
    }

    @Test
    void testNormalizeLicenseUrl() {
        assertThat(LicenseUtils.normalizeLicenseUrl("https://www.opensource.org/licenses/cddl1.php"))
                .isEqualTo("opensource.org/licenses/cddl1");
        assertThat(LicenseUtils.normalizeLicenseUrl("https://creativecommons.org/publicdomain/zero/1.0/"))
                .isEqualTo("cc.org/publicdomain/zero/1.0");
    }

    @Test
    void tesContainsWordsInSameOrder() {
        String name = "Apache License, Version 2.0";
        String id = "Apache-2.0";
        assertThat(LicenseUtils.containsWordsInSameOrder(name, id)).isTrue();
    }

    static Stream<Arguments> stringListStringProvider() {
        return Stream.of(
                arguments(
                        "https://repository.jboss.org/licenses/apache-2.0.txt",
                        List.of("apache", "2", "0"),
                        "Apache-2.0"),
                arguments("https://repository.jboss.org/licenses/cc0-1.0.txt", List.of("cc0", "1", "0"), "CC0-1.0"),
                arguments("https://www.eclipse.org/legal/epl-2.0/", List.of("epl", "2", "0"), "EPL-2.0"),
                arguments("http://www.eclipse.org/org/documents/epl-v10.php", List.of("epl", "1", "0"), "EPL-1.0"),
                arguments("http://www.eclipse.org/legal/epl-v20.html", List.of("epl", "2", "0"), "EPL-2.0"),
                arguments("http://www.opensource.org/licenses/cpl1.0.txt", List.of("cpl", "1", "0"), "CPL-1.0"),
                arguments("http://oss.oracle.com/licenses/upl", List.of("upl"), "UPL"));
    }

    @ParameterizedTest
    @MethodSource("stringListStringProvider")
    void tesContainsWordsInSameOrderUrl1(String licenseUrl, List<String> tokens, String licenseId) {
        String[] tokens1 = LicenseUtils.tokenizeLicenseString(licenseUrl);
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
    void testGetNumberOfSpdxLicenses() {
        assertThat(LicenseUtils.getNumberOfSpdxLicenses()).isPositive();
    }

    @Test
    void testGetSpdxLicenseListVersion() {
        assertThat(LicenseUtils.getSpdxLicenseListVersion()).isNotEmpty();
    }

    @Test
    void testParseSPDXLicenseString() throws InvalidLicenseStringException {
        assertThat(LicenseUtils.parseSPDXLicenseString("(GPL-2.0 WITH Universal-FOSS-exception-1.0)"))
                .isInstanceOf(WithExceptionOperator.class);
    }

    @Test
    void testFindLicenseMapping() {
        assertThat(LicenseUtils.findLicenseMapping(MAPPING, "Apache License")).hasValue("Apache-2.0");
        assertThat(LicenseUtils.findLicenseMapping(MAPPING, "https://www.freemarker.org/LICENSE.txt"))
                .hasValue("Apache-2.0");
    }

    @Test
    void testFindMatchingLicense() {
        assertThat(LicenseUtils.findMatchingLicense("Apache License 2.0", "https://www.freemarker.org/LICENSE.txt"))
                .hasValue("Apache-2.0");
        assertThat(LicenseUtils.findMatchingLicense("AL2", "https://repository.jboss.org/licenses/apache-2.0.txt"))
                .hasValue("Apache-2.0");
    }
}
