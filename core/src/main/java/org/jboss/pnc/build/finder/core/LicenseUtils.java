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
import static org.spdx.library.SpdxConstants.NOASSERTION_VALUE;
import static org.spdx.library.SpdxConstants.NONE_VALUE;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
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

/**
 * Utilities for working with SPDX licenses.
 */
public final class LicenseUtils {
    public static final String LICENSE_MAPPING_FILENAME = "license-mapping.json";

    public static final String NOASSERTION = NOASSERTION_VALUE;

    public static final String NONE = NONE_VALUE;

    private static final Pattern IDSTRING_PATTERN = Pattern.compile("[a-zA-Z0-9-]+");

    private static final int EXPECTED_NUM_SPDX_LICENSES = 1024;

    private static Map<String, SpdxListedLicense> LICENSE_ID_MAP;

    private static Map<String, SpdxListedLicense> LICENSE_NAME_MAP;

    private static List<String> LICENSE_IDS;

    private static List<String> LICENSE_NAMES;

    static {
        LICENSE_ID_MAP = new HashMap<>(EXPECTED_NUM_SPDX_LICENSES);
        LICENSE_NAME_MAP = new HashMap<>(EXPECTED_NUM_SPDX_LICENSES);
        LICENSE_IDS = new ArrayList<>(EXPECTED_NUM_SPDX_LICENSES);
        LICENSE_NAMES = new ArrayList<>(EXPECTED_NUM_SPDX_LICENSES);
        List<String> spdxListedLicenseIds = LicenseInfoFactory.getSpdxListedLicenseIds();

        for (String id : spdxListedLicenseIds) {
            try {
                SpdxListedLicense spdxListedLicense = LicenseInfoFactory.getListedLicenseById(id);
                String licenseId = spdxListedLicense.getLicenseId();
                LICENSE_ID_MAP.put(licenseId, spdxListedLicense);
                LICENSE_IDS.add(licenseId);
                String licenseName = spdxListedLicense.getName();
                LICENSE_NAME_MAP.put(licenseName, spdxListedLicense);
                LICENSE_NAMES.add(licenseName);
            } catch (InvalidSPDXAnalysisException e) {
                throw new RuntimeException(e);
            }
        }

        LICENSE_ID_MAP = Collections.unmodifiableMap(LICENSE_ID_MAP);
        LICENSE_NAME_MAP = Collections.unmodifiableMap(LICENSE_NAME_MAP);
        LICENSE_IDS.sort(comparing(String::length).reversed().thenComparing(naturalOrder()));
        LICENSE_IDS = Collections.unmodifiableList(LICENSE_IDS);
        LICENSE_NAMES.sort(comparing(String::length).reversed().thenComparing(naturalOrder()));
        LICENSE_NAMES = Collections.unmodifiableList(LICENSE_NAMES);
    }

    private static final String URL_MARKER = ":/";

    private static final Pattern PUNCT_PATTERN = Pattern.compile("\\p{Punct}");

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final String[] EXTENSIONS_TO_REMOVE = { ".html", ".php", ".txt" };

    private static final Pattern NAME_VERSION_PATTERN = Pattern
            .compile("(?<name>[A-Z-a-z])[Vv]?(?<major>[1-9]+)(\\.(?<minor>[0-9]+))?");

    private LicenseUtils() {
        throw new IllegalArgumentException("This is a utility class and cannot be instantiated");
    }

    /**
     * Loads the licenses mapping JSON file (<code>license-mapping.json</code>) into a map. The map keys consist of SPDX
     * license short identifiers, e.g., <code>Apache-2.0</code>). The values are either a license URL or license name.
     * These fields correspond to the values in the Maven POM for the license. It is generally preferred to use a URL as
     * a name may be ambiguous, e.g., how do we know which BSD license the name &quot;BSD&quot; refers to.
     * <p/>
     * The license file is validated while loading in order to make sure that the license identifiers are valid.
     * <em>Note that even though SPDX identifiers are considered to be case-insensitive, the mapping file requires the
     * canonical names of the license identifiers from the license list.</em>
     *
     * @return the license mapping map
     * @throws IOException if an error occurs reading the file or parsing the JSON
     */
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
            if (IDSTRING_PATTERN.matcher(licenseString).matches()) {
                validateSpdxListedLicenseId(licenseString);
                continue;
            }

            try {
                AnyLicenseInfo anyLicenseInfo = parseSPDXLicenseString(licenseString);

                if (anyLicenseInfo instanceof SpdxListedLicense) {
                    SpdxListedLicense spdxListedLicense = (SpdxListedLicense) anyLicenseInfo;
                    String licenseId = spdxListedLicense.getLicenseId();
                    validateSpdxListedLicenseId(licenseId);
                }
            } catch (InvalidLicenseStringException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void validateSpdxListedLicenseId(String licenseString) {
        if (NOASSERTION.equals(licenseString) || NONE.equals(licenseString)
                || LICENSE_ID_MAP.containsKey(licenseString)) {
            return;
        }

        throw new RuntimeException("License identifier '" + licenseString + "' is not in list of SPDX licenses");
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

    /**
     * Tries to find the given license string in the license map. The license string may be either a license name or a
     * license URL. A URL is detected automatically by looking for <code>:/</code> in the license string. If a URL is
     * detected, the URLs are then normalized. The normalization is a follows:
     * <ol>
     * <li>Call <code>URI::normalize</code></li>
     * <li>Remove www. from the host</li>
     * <li>Replace creativecommins with cc in the host (since CC is used for the SPDX short license identifiers)</li>
     * <li>Remove any trailing slash</li>
     * </ol>
     *
     * @param mapping the license mapping
     * @param licenseString the license string (either a name or URL)
     * @return the mapping (or empty if none)
     */
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

    /**
     * Gets the Version of the license list being used by the SPDX Java library.
     *
     *
     * @return the version of the license list
     */
    public static String getSpdxLicenseListVersion() {
        return LicenseInfoFactory.getLicenseListVersion();
    }

    static AnyLicenseInfo parseSPDXLicenseString(String licenseString) throws InvalidLicenseStringException {
        return LicenseInfoFactory.parseSPDXLicenseString(licenseString, null, null, null);
    }

    public static int getNumberOfSpdxLicenses() {
        return LICENSE_ID_MAP.size();
    }

    /**
     * Finds an exact match for the given SPDX license short identifier.
     *
     * @param licenseId the SPDX license short identifier
     * @return the SPDX license short identifier if present, or empty otherwise
     */
    public static Optional<String> findMatchingLicenseId(String licenseId) {
        if (licenseId == null || licenseId.isBlank()) {
            return Optional.empty();
        }

        SpdxListedLicense license = LICENSE_ID_MAP.get(licenseId);
        return Optional.ofNullable(license != null ? license.getId() : null);
    }

    /**
     * Finds a match for the given Maven license name or URL in the SPDX license id list or SPDX license name list. The
     * SPDX lists are first sorted in order of descending length, then in natural order. This ensures that the match is
     * the one that contains the most words in order if such as search needs to be performed. A match is searched for in
     * the following order:
     * <ol>
     * <li>Check if the license name contains the same words as the SPDX license identifier</li>
     * <li>Check if the license URL (after normalization) contains the same words as the SPDX license identifier</li>
     * <li>Check if the license name is equal to the SPDX license name (ignoring case)</li>
     * <li>Check if the license name contains the same words as the SPDX license name</li>
     * </ol>
     * Words are searched for in order ignoring case and punctuation. Words are tokenized according to whitespace (for
     * the name) and slashes (for the URL). Additionally, numeric versions are currently treated as separate tokens,
     * e.g, 1.0 is treated as the word 1 and the word 0.
     *
     * @param licenseName the license name
     * @return the license URL (may be null)
     */
    public static Optional<String> findMatchingLicenseName(String licenseName, String licenseUrl) {
        for (String licenseId : LICENSE_IDS) {
            if (containsWordsInSameOrder(licenseName, licenseId) || containsWordsInSameOrder(licenseUrl, licenseId)) {
                return Optional.of(licenseId);
            }
        }

        for (String spdxLicenseName : LICENSE_NAMES) {
            if (spdxLicenseName.equalsIgnoreCase(licenseName)
                    || containsWordsInSameOrder(licenseName, spdxLicenseName)) {
                SpdxListedLicense spdxListedLicense = LICENSE_NAME_MAP.get(spdxLicenseName);
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

    /**
     * Finds a match for the URL (after normalization) in the SPDX seeAlso URL list.
     *
     * @param licenseUrl the license URL
     * @return the matching SPDX license URL if found, otherwise empty
     */
    public static Optional<String> findMatchingLicenseSeeAlso(String licenseUrl) {
        if (licenseUrl == null || licenseUrl.isBlank()) {
            return Optional.empty();
        }

        Collection<SpdxListedLicense> values = LICENSE_ID_MAP.values();

        for (SpdxListedLicense license : values) {
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

    /**
     * Finds a match for the given license name and license URL, if any, in the SPDX license list. A match is searched
     * for in the following order:
     * <ol>
     * <li>Search for a matching SPDX license identifier</li>
     * <li>Search for a match SPDX license name</li>
     * <li>Search for a match SPDX seeAlso URL</li>
     * </ol>
     *
     * @param licenseName the license name
     * @param licenseUrl the license URL
     * @return the match if any, otherwise empty
     */
    public static Optional<String> findMatchingLicense(String licenseName, String licenseUrl) {
        return findMatchingLicenseId(licenseName).or(() -> findMatchingLicenseName(licenseName, licenseUrl))
                .or(() -> findMatchingLicenseSeeAlso(licenseUrl));
    }
}
