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

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static org.spdx.library.DefaultModelStore.getDefaultCopyManager;
import static org.spdx.library.DefaultModelStore.getDefaultDocumentUri;
import static org.spdx.library.DefaultModelStore.getDefaultModelStore;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.model.license.AnyLicenseInfo;
import org.spdx.library.model.license.InvalidLicenseStringException;
import org.spdx.library.model.license.LicenseInfoFactory;
import org.spdx.library.model.license.SpdxListedLicense;

public final class LicenseUtils {
    public static final String LICENSE_MAPPING_FILENAME = "license-mapping.json";

    private static final Map<String, SpdxListedLicense> LICENSE_ID_MAP = getLicenseIdMap();

    private static final Map<String, SpdxListedLicense> LICENSE_NAME_MAP = getLicenseNameMap();

    private static final List<SpdxListedLicense> LICENSES = List.copyOf(LICENSE_ID_MAP.values());

    private static final List<String> LICENSE_IDS = LICENSES.stream()
            .map(SpdxListedLicense::getLicenseId)
            .sorted(comparing(String::length).reversed().thenComparing(naturalOrder()))
            .collect(Collectors.toUnmodifiableList());

    private static final List<String> LICENSE_NAMES = LICENSES.stream()
            .map(LicenseUtils::findLicenseName)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .sorted(comparing(String::length).reversed().thenComparing(naturalOrder()))
            .collect(Collectors.toUnmodifiableList());

    private static final String URL_MARKER = ":/";

    private static final Pattern PUNCT_PATTERN = Pattern.compile("\\p{Punct}");

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final int EXPECTED_NUM_SPDX_LICENSES = 1000;

    private static final String[] EXTENSIONS_TO_REMOVE = { ".html", ".php", ".txt" };

    private static final Pattern NAME_VERSION_PATTERN = Pattern
            .compile("(?<name>[A-Z-a-z])[Vv]?(?<major>[1-9]+)(\\.(?<minor>[0-9]+))?");

    private LicenseUtils() {
        throw new IllegalArgumentException("This is a utility class and cannot be instantiated");
    }

    private static Map<String, SpdxListedLicense> getLicenseIdMap() {
        Map<String, SpdxListedLicense> map = new HashMap<>(EXPECTED_NUM_SPDX_LICENSES);
        List<String> spdxListedLicenseIds = LicenseInfoFactory.getSpdxListedLicenseIds();

        for (String id : spdxListedLicenseIds) {
            try {
                SpdxListedLicense spdxListedLicense = LicenseInfoFactory.getListedLicenseById(id);
                map.put(spdxListedLicense.getLicenseId(), spdxListedLicense);
            } catch (InvalidSPDXAnalysisException e) {
                throw new RuntimeException(e);
            }
        }

        return Collections.unmodifiableMap(map);
    }

    private static Map<String, SpdxListedLicense> getLicenseNameMap() {
        Map<String, SpdxListedLicense> map = new HashMap<>(EXPECTED_NUM_SPDX_LICENSES);
        List<String> spdxListedLicenseIds = LicenseInfoFactory.getSpdxListedLicenseIds();

        for (String id : spdxListedLicenseIds) {
            try {
                SpdxListedLicense spdxListedLicense = LicenseInfoFactory.getListedLicenseById(id);
                map.put(spdxListedLicense.getName(), spdxListedLicense);
            } catch (InvalidSPDXAnalysisException e) {
                throw new RuntimeException(e);
            }
        }

        return Collections.unmodifiableMap(map);
    }

    public static Map<String, List<String>> loadLicenseMapping() throws IOException {
        try (InputStream in = LicenseUtils.class.getClassLoader().getResourceAsStream(LICENSE_MAPPING_FILENAME)) {
            Map<String, List<String>> mapping = JSONUtils.loadLicenseMappingUrls(in);
            validateLicenseMapping(mapping);
            return Collections.unmodifiableMap(mapping);
        }
    }

    private static void validateLicenseMapping(Map<String, List<String>> mapping) {
        Set<String> licenseStrings = mapping.keySet();

        for (String licenseString : licenseStrings) {
            try {
                parseSPDXLicenseString(licenseString);
            } catch (InvalidLicenseStringException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static String normalizeLicenseUrl(String licenseUrl) {
        URI uri = URI.create(licenseUrl).normalize();
        String host = Objects.requireNonNullElse(uri.getHost(), "");
        host = host.replace("www.", "");
        host = host.replace("creativecommons", "cc"); // XXX: Helps match license id
        String path = Objects.requireNonNullElse(uri.getPath(), "");
        path = StringUtils.removeEnd(path, "/");

        if (StringUtils.endsWithAny(path, EXTENSIONS_TO_REMOVE)) {
            path = FilenameUtils.removeExtension(path);
        }

        return host + path;
    }

    private static boolean isUrl(String... strings) {
        for (String s : strings) {
            if (s == null || !s.contains(URL_MARKER)) {
                return false;
            }
        }

        return true;
    }

    public static Optional<String> findLicenseMapping(Map<String, List<String>> mapping, String licenseString) {
        if (licenseString == null || licenseString.isBlank()) {
            return Optional.empty();
        }

        Set<Map.Entry<String, List<String>>> entries = mapping.entrySet();

        for (Map.Entry<String, List<String>> entry : entries) {
            String licenseId = entry.getKey();
            List<String> licenseNamesOrUrls = entry.getValue();

            for (String licenseNameOrUrl : licenseNamesOrUrls) {
                if (isUrl(licenseString, licenseNameOrUrl)) {
                    String normalizedLicenseString = normalizeLicenseUrl(licenseString);
                    String normalizedNameOrUrl = normalizeLicenseUrl(licenseNameOrUrl);

                    if (normalizedLicenseString.equals(normalizedNameOrUrl)) {
                        return Optional.of(licenseId);
                    }
                } else if (licenseString.equalsIgnoreCase(licenseNameOrUrl)) {
                    return Optional.of(licenseId);
                }
            }
        }

        return Optional.empty();
    }

    public static String getSpdxLicenseListVersion() {
        return LicenseInfoFactory.getLicenseListVersion();
    }

    public static AnyLicenseInfo parseSPDXLicenseString(String licenseString) throws InvalidLicenseStringException {
        return LicenseInfoFactory.parseSPDXLicenseString(
                licenseString,
                getDefaultModelStore(),
                getDefaultDocumentUri(),
                getDefaultCopyManager());
    }

    public static int getNumberOfSpdxLicenses() {
        return LICENSES.size();
    }

    public static Optional<String> findMatchingLicenseId(String licenseId) {
        if (licenseId == null || licenseId.isBlank()) {
            return Optional.empty();
        }

        SpdxListedLicense license = LICENSE_ID_MAP.get(licenseId);
        return Optional.ofNullable(license != null ? license.getId() : null);
    }

    public static Optional<String> findMatchingLicenseName(String name, String url) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        for (String licenseId : LICENSE_IDS) {
            if (containsWordsInSameOrder(name, licenseId) || containsWordsInSameOrder(url, licenseId)) {
                return Optional.of(licenseId);
            }
        }

        for (String licenseName : LICENSE_NAMES) {
            if (licenseName.equalsIgnoreCase(name) || containsWordsInSameOrder(name, licenseName)) {
                SpdxListedLicense spdxListedLicense = LICENSE_NAME_MAP.get(licenseName);
                String licenseId = spdxListedLicense.getLicenseId();
                return Optional.of(licenseId);
            }
        }

        return Optional.empty();
    }

    static String[] tokenizeLicenseString(String licenseString) {
        if (isUrl(licenseString)) {
            licenseString = normalizeLicenseUrl(licenseString);
            licenseString = licenseString.replace('/', '-').replace('.', '-');
        }

        licenseString = licenseString.replace('.', '-').replace('-', ' ');
        licenseString = PUNCT_PATTERN.matcher(licenseString).replaceAll("");
        licenseString = NAME_VERSION_PATTERN.matcher(licenseString).replaceAll("${name} ${major} ${minor}");
        licenseString = licenseString.toLowerCase(Locale.ROOT);
        return WHITESPACE_PATTERN.split(licenseString);
    }

    static boolean containsWordsInSameOrder(String licenseStringCandidate, String licenseString) {
        if (licenseStringCandidate == null || licenseString == null) {
            return false;
        }

        String[] array = tokenizeLicenseString(licenseStringCandidate);
        String[] searchStrings = tokenizeLicenseString(licenseString);
        int startIndex = 0;

        for (String objectToFind : searchStrings) {
            int index = ArrayUtils.indexOf(array, objectToFind, startIndex);

            if (index < startIndex++) {
                return false;
            }
        }

        return true;
    }

    public static Optional<String> findMatchingLicenseSeeAlso(String licenseUrl) {
        if (licenseUrl == null || licenseUrl.isBlank()) {
            return Optional.empty();
        }

        for (SpdxListedLicense license : LICENSES) {
            try {
                List<String> seeAlso = license.getSeeAlso()
                        .stream()
                        .map(LicenseUtils::normalizeLicenseUrl)
                        .collect(Collectors.toList());

                if (seeAlso.contains(normalizeLicenseUrl(licenseUrl))) {
                    return Optional.of(license.getLicenseId());
                }
            } catch (InvalidSPDXAnalysisException ignored) {

            }
        }

        return Optional.empty();
    }

    public static Optional<String> findMatchingLicense(String licenseName, String licenseUrl) {
        Optional<String> optLicenseId = findMatchingLicenseId(licenseName);

        if (optLicenseId.isPresent()) {
            return optLicenseId;
        }

        Optional<String> optLicenseId2 = findMatchingLicenseName(licenseName, licenseUrl);

        if (optLicenseId2.isPresent()) {
            return optLicenseId2;
        }

        return findMatchingLicenseSeeAlso(licenseUrl);
    }

    private static Optional<String> findLicenseName(SpdxListedLicense spdxListedLicense) {
        try {
            return Optional.of(spdxListedLicense.getName());
        } catch (InvalidSPDXAnalysisException e) {
            return Optional.empty();
        }
    }
}
