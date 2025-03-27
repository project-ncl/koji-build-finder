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
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.spdx.library.LicenseInfoFactory.NOASSERTION_LICENSE_NAME;
import static org.spdx.library.LicenseInfoFactory.NONE_LICENSE_NAME;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.spdx.core.DefaultStoreNotInitializedException;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.library.LicenseInfoFactory;
import org.spdx.library.model.v2.license.InvalidLicenseStringException;
import org.spdx.library.model.v3_0_1.expandedlicensing.ListedLicense;
import org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo;
import org.spdx.utility.compare.LicenseCompareHelper;
import org.spdx.utility.compare.SpdxCompareException;

import com.fasterxml.jackson.core.type.TypeReference;

public final class SpdxLicenseUtils {
    static final String NOASSERTION = NOASSERTION_LICENSE_NAME;

    static final String NONE = NONE_LICENSE_NAME;

    private static final List<String> LICENSE_IDS_TEXT_LIST = List
            .of("Apache-2.0", "BSD-3-Clause", "EPL-1.0", "BSD-2-Clause", "MIT", "xpp", "Plexus");

    private static final String LICENSE_MAPPING_FILENAME = "build-finder-license-mapping.json";

    private static final String LICENSE_DEPRECATED_FILENAME = "build-finder-license-deprecated.json";

    static final Pattern IDSTRING_PATTERN = Pattern.compile("[a-zA-Z0-9-.]+");

    private static final String LICENSE = "LICENSE";

    private static final Pattern SPDX_LICENSE_IDENTIFIER_PATTERN = Pattern
            .compile("SPDX-License-Identifier:\\s*(" + IDSTRING_PATTERN.pattern() + ")");

    private static final int EXPECTED_NUM_SPDX_LICENSES = 1024;

    private static final Map<String, List<String>> LICENSE_MAPPINGS_MAP;

    private static Map<String, ListedLicense> LICENSE_IDS_MAP;

    private static Map<String, ListedLicense> LICENSE_NAMES_MAP;

    private static final Map<String, String> LICENSE_IDS_DEPRECATED_MAP;

    private static List<String> LICENSE_IDS_LIST;

    private static List<String> LICENSE_NAMES_LIST;

    static {
        LICENSE_IDS_MAP = new LinkedHashMap<>(EXPECTED_NUM_SPDX_LICENSES);
        List<String> listedLicenseIds = getListedLicenseIds();
        LICENSE_IDS_LIST = listedLicenseIds.stream()
                .sorted(comparing(String::length).reversed().thenComparing(naturalOrder()))
                .toList();
        LICENSE_NAMES_MAP = new LinkedHashMap<>(EXPECTED_NUM_SPDX_LICENSES);
        LICENSE_NAMES_LIST = new ArrayList<>(EXPECTED_NUM_SPDX_LICENSES);

        for (String id : LICENSE_IDS_LIST) {
            Utils.retry(() -> addLicenseIdToMaps(id));
        }

        LICENSE_IDS_MAP = Collections.unmodifiableMap(LICENSE_IDS_MAP);
        LICENSE_NAMES_MAP = Collections.unmodifiableMap(LICENSE_NAMES_MAP);
        LICENSE_IDS_LIST = Collections.unmodifiableList(LICENSE_IDS_LIST);
        LICENSE_NAMES_LIST.sort(comparing(String::length).reversed().thenComparing(naturalOrder()));
        LICENSE_NAMES_LIST = Collections.unmodifiableList(LICENSE_NAMES_LIST);

        try {
            // XXX: Should be moved to an external file
            LICENSE_IDS_DEPRECATED_MAP = loadLicenseDeprecated();

            try (InputStream in = LicenseUtils.class.getClassLoader()
                    .getResourceAsStream(LICENSE_MAPPING_FILENAME)) {
                LICENSE_MAPPINGS_MAP = Collections.unmodifiableMap(JSONUtils.loadLicenseMapping(in));
                validateLicenseMapping();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private SpdxLicenseUtils() {
        throw new IllegalArgumentException("This is a utility class and cannot be instantiated");
    }

    private static List<String> getListedLicenseIds() {
        return Utils.retry(LicenseInfoFactory::getSpdxListedLicenseIds);
    }

    private static ListedLicense addLicenseIdToMaps(String id) {
        try {
            ListedLicense listedLicense = LicenseInfoFactory.getListedLicenseById(id);
            LICENSE_IDS_MAP.put(listedLicense.getId(), listedLicense);
            listedLicense.getName().ifPresent(licenseName -> {
                LICENSE_NAMES_MAP.put(licenseName, listedLicense);
                LICENSE_NAMES_LIST.add(licenseName);
            });
            return listedLicense;
        } catch (InvalidSPDXAnalysisException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static void validateLicenseMapping() {
        Set<String> licenseStrings = LICENSE_MAPPINGS_MAP.keySet();

        for (String licenseString : licenseStrings) {
            if (IDSTRING_PATTERN.matcher(licenseString).matches()) {
                validateSPDXListedLicenseId(licenseString);
                continue;
            }

            validateLicenseString(licenseString);
        }
    }

    private static Map<String, String> loadLicenseDeprecated() throws IOException {
        try (InputStream in = LicenseUtils.class.getClassLoader().getResourceAsStream(LICENSE_DEPRECATED_FILENAME)) {
            Map<String, String> map = new BuildFinderObjectMapper()
                    .readValue(in, new TypeReference<LinkedHashMap<String, String>>() {
                    });
            validateLicenseDeprecated(map);
            return Collections.unmodifiableMap(map);
        }
    }

    /**
     * Tries to find the given license string in the license map. The license string may be either a license name or a
     * license URL. A URL is detected automatically by looking for <code>:/</code> in the license string. If a URL is
     * detected, the URLs are then normalized. The normalization is as follows:
     * <ol>
     * <li>Call <code>URI::normalize</code></li>
     * <li>Remove "www." from the host</li>
     * <li>Replace creativecommons with cc in the host (since CC is used for the SPDX short license identifiers)</li>
     * <li>Remove any trailing slash</li>
     * </ol>
     *
     * @param licenseString the license string (either a name or URL)
     * @return the mapping (or empty if none)
     */
    public static Optional<String> findLicenseMapping(String licenseString) {
        return findLicenseMapping(LICENSE_MAPPINGS_MAP, licenseString);
    }

    public static String getSPDXLicenseName(String licenseId) {
        if (licenseId == null) {
            return EMPTY;
        }

        String currentLicenseId = getCurrentLicenseId(licenseId);
        ListedLicense spdxListedLicense = LICENSE_IDS_MAP.get(currentLicenseId);

        if (spdxListedLicense == null) {
            return EMPTY;
        }

        try {
            return spdxListedLicense.getName().orElse(EMPTY);
        } catch (InvalidSPDXAnalysisException e) {
            return EMPTY;
        }
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
     * e.g., 1.0 is treated as the word "1" and the word "0".
     *
     * @param licenseName the license name
     * @return the license URL (which may be <code>null</code>)
     */
    public static Optional<String> findMatchingLicenseName(String licenseName, String licenseUrl) {
        for (String licenseId : LICENSE_IDS_LIST) {
            if (LicenseUtils.containsWordsInSameOrder(licenseName, StringUtils.replace(licenseId, "-only", ""))
                    || LicenseUtils.containsWordsInSameOrder(licenseUrl, licenseId)) {
                return Optional.of(getCurrentLicenseId(licenseId));
            }
        }

        for (String spdxLicenseName : LICENSE_NAMES_LIST) {
            if (spdxLicenseName.equalsIgnoreCase(licenseName)
                    || LicenseUtils
                            .containsWordsInSameOrder(licenseName, StringUtils.replace(spdxLicenseName, " only", ""))
                    || LicenseUtils.containsWordsInSameOrder(licenseUrl, spdxLicenseName)) {
                ListedLicense listedLicense = LICENSE_NAMES_MAP.get(spdxLicenseName);
                String licenseId = getCurrentLicenseId(listedLicense.getId());
                return Optional.of(licenseId);
            }
        }

        return Optional.empty();
    }

    /**
     * Finds a matching SPDX license identifier for the text in the given license text file, if any.
     *
     * @param licenseFileObject the license text file
     * @return the matching license identifier, if any
     */
    public static Optional<String> findMatchingLicense(FileObject licenseFileObject) {
        Optional<String> optionalId = findSPDXIdentifierFromFileName(licenseFileObject);

        if (optionalId.isPresent()) {
            return optionalId;
        }

        try (FileContent fc = licenseFileObject.getContent(); InputStream in = fc.getInputStream()) {
            String licenseText = new String(in.readAllBytes(), UTF_8);
            return LICENSE_IDS_TEXT_LIST.stream()
                    .map(id -> LICENSE_IDS_MAP.get(id))
                    .map(license -> findMatchingSPDXLicenseIdentifier(license, licenseText))
                    .flatMap(Optional::stream)
                    .findAny()
                    .or(() -> findMatchingSPDXLicenseIdentifierOrLicense(licenseText));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Returns whether this SPDX license identifier is known.
     *
     * @param licenseId the SPDX license identifier
     */
    public static boolean isKnownLicenseId(String licenseId) {
        return LICENSE_IDS_LIST.contains(licenseId);
    }

    /**
     * Gets the SPDX license identifier using the loaded mappings.
     *
     * @param name the name
     * @param url the URL
     * @return the matching SPDX license identifier, or <code>NOASSERTION</code> if no match
     */
    public static String getSPDXLicenseId(String name, String url) {
        return getSPDXLicenseId(LICENSE_MAPPINGS_MAP, name, url);
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

        ListedLicense listedLicense = LICENSE_IDS_MAP.get(licenseId);
        return Optional.ofNullable(listedLicense != null ? getCurrentLicenseId(listedLicense.getId()) : null);
    }

    static String getCurrentLicenseId(String licenseId) {
        String currentId = LICENSE_IDS_DEPRECATED_MAP.get(licenseId);
        return currentId != null ? currentId : licenseId;
    }

    public static int getNumberOfSPDXLicenses() {
        return LICENSE_IDS_MAP.size();
    }

    static Optional<String> findMatchingLicenseSeeAlso(String licenseUrl) {
        if (!LicenseUtils.isUrl(licenseUrl)) {
            return Optional.empty();
        }

        Collection<ListedLicense> values = LICENSE_IDS_MAP.values();

        for (ListedLicense listedLicense : values) {
            List<String> seeAlso = listedLicense.getSeeAlsos()
                    .stream()
                    .filter(LicenseUtils::isUrl)
                    .map(LicenseUtils::normalizeLicenseUrl)
                    .toList();

            if (seeAlso.contains(LicenseUtils.normalizeLicenseUrl(licenseUrl))) {
                return Optional.of(getCurrentLicenseId(listedLicense.getId()));
            }
        }

        return Optional.empty();
    }

    /**
     * Gets the license mapping.
     * <p/>
     * The license mapping JSON file (<code>build-finder-license-mapping.json</code>) is loaded into a map. The map keys
     * consist of SPDX license short identifiers, e.g., <code>Apache-2.0</code>). The values are either a license URL or
     * license name. These fields correspond to the values in the Maven POM for the license. It is generally preferred
     * to use a URL as a name may be ambiguous, e.g., how do we know which BSD license the name "BSD" refers
     * to.
     * <p/>
     * The license file is validated while loading to ensure that the license identifiers are valid.
     * <em>Note that even though SPDX identifiers are considered to be case-insensitive, the mapping file requires the
     * canonical names of the license identifiers from the license list.</em>
     *
     * @return the license mapping map
     */
    public static Map<String, List<String>> getSpdxLicenseMapping() {
        return LICENSE_MAPPINGS_MAP;
    }

    static void validateLicenseDeprecated(Map<String, String> map) {
        map.forEach((key, value) -> {
            validateLicenseString(key);
            validateLicenseString(value);
        });
    }

    static Optional<String> findSPDXLicenseId(Map<String, List<String>> mapping, String name, String url) {
        Optional<String> optSPDXLicenseId = findLicenseMapping(mapping, url).or(() -> findMatchingLicense(name, url));
        return optSPDXLicenseId.or(() -> findLicenseMapping(mapping, name));
    }

    /**
     * Finds a match for the given license name and license URL, if any, in the SPDX license list. A match is searched
     * for in the following order:
     *
     * <ol>
     * <li>Search for a matching SPDX license identifier</li>
     * <li>Search for a match SPDX license name</li>
     * <li>Search for a match SPDX seeAlso URL</li>
     * </ol>
     *
     * @param licenseName the license name
     * @param licenseUrl the license URL
     * @return the match, if any, otherwise empty
     */
    public static Optional<String> findMatchingLicense(String licenseName, String licenseUrl) {
        return findMatchingLicenseId(licenseName).or(() -> findMatchingLicenseName(licenseName, licenseUrl))
                .or(() -> findMatchingLicenseSeeAlso(licenseUrl));
    }

    static Optional<String> findSPDXIdentifierFromFileName(FileObject fileObject) {
        try {
            if (!fileObject.isFile()) {
                return Optional.empty();
            }
        } catch (FileSystemException e) {
            return Optional.empty();
        }

        String path = fileObject.getName().getPath();
        String name = FilenameUtils.getName(path);
        String baseName = FilenameUtils.removeExtension(name);

        if (isKnownLicenseId(baseName)) {
            return Optional.of(baseName);
        }

        return Optional.empty();
    }

    static void validateSPDXListedLicenseId(String licenseString) {
        if (isUnknownLicenseId(licenseString) || isKnownLicenseId(licenseString)) {
            return;
        }

        throw new IllegalArgumentException(
                "License identifier '" + licenseString + "' is not in list of SPDX licenses");
    }

    static void validateLicenseString(String licenseString) {
        try {
            AnyLicenseInfo anyLicenseInfo = parseSPDXLicenseString(licenseString);

            if (anyLicenseInfo instanceof ListedLicense listedLicense) {
                validateSPDXListedLicenseId(listedLicense.getId());
            }
        } catch (InvalidLicenseStringException | DefaultStoreNotInitializedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static Optional<String> findMatchingSPDXLicenseIdentifierOrLicense(String text) {
        String lines = LicenseUtils.licenseFileToText(text);
        return findMatchingSPDXLicenseIdentifier(lines).or(() -> findMatchingLicense(lines, null));
    }

    /**
     * Gets the matching SPDX license identifier for the text in the given license text file, or
     * <code></code>NOASSERTION</code>.
     *
     * @param licenseFileObject the license text file
     * @return the matching license identifier or <code>NOASSERTION</code> if no match
     */
    public static String getMatchingLicense(FileObject licenseFileObject) {
        return findMatchingLicense(licenseFileObject).orElse(NOASSERTION);
    }

    /**
     * Finds the license from the SPDX-License-Identifier token, if any
     *
     * @param licenseString the license string
     * @return the optional matching license identifier, or empty if no match
     */
    public static Optional<String> findMatchingSPDXLicenseIdentifier(String licenseString) {
        Matcher matcher = SPDX_LICENSE_IDENTIFIER_PATTERN.matcher(licenseString);

        if (!matcher.find()) {
            return Optional.empty();
        }

        String id = matcher.group(1);
        return Optional.of(getCurrentLicenseId(id));
    }

    static Optional<String> findMatchingSPDXLicenseIdentifier(ListedLicense listedLicense, String licenseText) {
        try {
            if (!LicenseCompareHelper.isTextStandardLicense(listedLicense, licenseText).isDifferenceFound()) {
                return Optional.of(getCurrentLicenseId(listedLicense.getId()));
            }
        } catch (SpdxCompareException | InvalidSPDXAnalysisException e) {
            // ignore
        }

        return Optional.empty();
    }

    /**
     * Returns whether the given file name is a license text file. Matches files such as
     *
     * <ul>
     * <li>LICENSE.md</li>
     * <li>LICENSE</li>
     * <li>LICENSE.txt</li>
     * <li>MIT-LICENSE</li>
     * <li>&lt;SPDX-LICENSE-ID&gt;.txt</li>
     * </ul>
     *
     * @param fileName the file name
     * @return whether the given file name is a license text file
     */
    public static boolean isLicenseFileName(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return false;
        }

        String extension = FilenameUtils.getExtension(fileName);

        if (!LicenseUtils.isTextExtension(extension)) {
            return false;
        }

        String name = FilenameUtils.getName(fileName);
        String baseName = FilenameUtils.removeExtension(name);

        return StringUtils.containsIgnoreCase(baseName, LICENSE) || isKnownLicenseId(baseName);
    }

    /**
     * Returns whether the given file name is a license text file. Matches files such as
     *
     * <ul>
     * <li>LICENSE.md</li>
     * <li>LICENSE</li>
     * <li>LICENSE.txt</li>
     * <li>MIT-LICENSE</li>
     * <li>&lt;SPDX-LICENSE-ID&gt;.txt</li>
     * </ul>
     *
     * @param fileObject the file object
     * @return whether the given file object is a license text file
     */
    public static boolean isLicenseFile(FileObject fileObject) {
        try {
            if (!fileObject.isFile()) {
                return false;
            }
        } catch (FileSystemException e) {
            return false;
        }

        String path = fileObject.getName().getPath();
        return isLicenseFileName(path);
    }

    static Optional<String> findLicenseMapping(Map<String, List<String>> mapping, String licenseString) {
        if (StringUtils.isBlank(licenseString)) {
            return Optional.empty();
        }

        Set<Map.Entry<String, List<String>>> entries = mapping.entrySet();

        for (Map.Entry<String, List<String>> entry : entries) {
            String licenseId = entry.getKey();
            List<String> licenseNamesOrUrls = entry.getValue();

            for (String licenseNameOrUrl : licenseNamesOrUrls) {
                if (LicenseUtils.isUrl(licenseString, licenseNameOrUrl)) {
                    String normalizedLicenseString = LicenseUtils.normalizeLicenseUrl(licenseString);
                    String normalizedNameOrUrl = LicenseUtils.normalizeLicenseUrl(licenseNameOrUrl);

                    if (normalizedLicenseString.equals(normalizedNameOrUrl)) {
                        return Optional.of(getCurrentLicenseId(licenseId));
                    }
                } else {
                    String normalizedString = StringUtils.normalizeSpace(licenseString);
                    String normalizedNameOrUrl = StringUtils.normalizeSpace(licenseNameOrUrl);

                    if (StringUtils.equalsIgnoreCase(normalizedNameOrUrl, normalizedString)) {
                        return Optional.of(getCurrentLicenseId(licenseId));
                    }
                }
            }
        }

        return Optional.empty();
    }

    static String getSPDXLicenseId(Map<String, List<String>> mapping, String name, String url) {
        return findSPDXLicenseId(mapping, name, url).orElse(NOASSERTION);
    }

    /**
     * Returns whether this SPDX license identifier is unknown (<code>NOASSERTION</code> or <code>NONE</code>).
     *
     * @param licenseId the SPDX license identifier
     */
    public static boolean isUnknownLicenseId(String licenseId) {
        return NOASSERTION.equals(licenseId) || NONE.equals(licenseId);
    }

    static AnyLicenseInfo parseSPDXLicenseString(String licenseString)
            throws InvalidLicenseStringException, DefaultStoreNotInitializedException {
        return LicenseInfoFactory.parseSPDXLicenseString(licenseString);
    }

    /**
     * Gets the Version of the license list being used by the SPDX Java library.
     *
     *
     * @return the version of the license list
     */
    public static String getSPDXLicenseListVersion() {
        return LicenseInfoFactory.getLicenseListVersion();
    }
}
