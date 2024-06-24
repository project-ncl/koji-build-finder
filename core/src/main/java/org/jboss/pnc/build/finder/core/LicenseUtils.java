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
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.spdx.library.SpdxConstants.NOASSERTION_VALUE;
import static org.spdx.library.SpdxConstants.NONE_VALUE;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.model.license.AnyLicenseInfo;
import org.spdx.library.model.license.InvalidLicenseStringException;
import org.spdx.library.model.license.LicenseInfoFactory;
import org.spdx.library.model.license.SpdxListedLicense;
import org.spdx.utility.compare.LicenseCompareHelper;
import org.spdx.utility.compare.SpdxCompareException;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Utilities for working with SPDX licenses.
 */
public final class LicenseUtils {
    static final String NOASSERTION = NOASSERTION_VALUE;

    static final String NONE = NONE_VALUE;

    private static final List<String> LICENSE_ID_TEXT_LIST = List
            .of("Apache-2.0", "BSD-3-Clause", "EPL-1.0", "BSD-2-Clause", "MIT", "xpp", "Plexus");

    private static final String LICENSE_MAPPING_FILENAME = "license-mapping.json";

    private static final String LICENSE_DEPRECATED_FILENAME = "license-deprecated.json";

    private static final Pattern IDSTRING_PATTERN = Pattern.compile("[a-zA-Z0-9-.]+");

    private static final Pattern LICENSE_FILE_PATTERN = Pattern.compile("^([A-Z-]+)?LICENSE(.md|.txt)?$");

    private static final Pattern MANIFEST_MF_PATTERN = Pattern.compile("^.*META-INF/MANIFEST.MF$");

    private static final int EXPECTED_NUM_SPDX_LICENSES = 1024;

    private static final String BUNDLE_LICENSE = "Bundle-License";

    private static final Pattern PUNCT_PATTERN = Pattern.compile("\\p{Punct}");

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final Pattern TWO_DIGIT_PATTERN = Pattern.compile("([0-9])([0-9])");

    private static final Pattern LETTER_DIGIT_PATTERN = Pattern.compile("([A-Za-z])([0-9])");

    private static final int LINE_LIMIT = 5;

    private static final Pattern SPDX_LICENSE_IDENTIFIER_PATTERN = Pattern
            .compile("SPDX-License-Identifier:\\s*(" + IDSTRING_PATTERN.pattern() + ")");

    private static Map<String, SpdxListedLicense> LICENSE_ID_MAP;

    private static Map<String, SpdxListedLicense> LICENSE_NAME_MAP;

    private static List<String> LICENSE_IDS;

    private static List<String> LICENSE_NAMES;

    private static final String URL_MARKER = ":/";

    private static final List<String> EXTENSIONS_TO_REMOVE = List.of(".html", ".php", ".txt");

    private static final Pattern NAME_VERSION_PATTERN = Pattern
            .compile("(?<name>[A-Z-a-z])[Vv]?(?<major>[1-9]+)(\\.(?<minor>[0-9]+))?");

    private static final Pattern SINGLE_DIGIT_PATTERN = Pattern.compile("(?<b>[^0-9.])(?<major>[1-9])(?<a>[^0-9.])");

    // XXX: Should be moved to an external file
    private static final Map<String, String> DEPRECATED_LICENSE_IDS_MAP;

    static {
        LICENSE_ID_MAP = new LinkedHashMap<>(EXPECTED_NUM_SPDX_LICENSES);
        LICENSE_IDS = LicenseInfoFactory.getSpdxListedLicenseIds()
                .stream()
                .sorted(comparing(String::length).reversed().thenComparing(naturalOrder()))
                .collect(Collectors.toUnmodifiableList());
        LICENSE_NAME_MAP = new LinkedHashMap<>(EXPECTED_NUM_SPDX_LICENSES);
        LICENSE_NAMES = new ArrayList<>(EXPECTED_NUM_SPDX_LICENSES);

        for (String id : LICENSE_IDS) {
            try {
                SpdxListedLicense spdxListedLicense = LicenseInfoFactory.getListedLicenseById(id);
                String licenseId = spdxListedLicense.getLicenseId();
                LICENSE_ID_MAP.put(licenseId, spdxListedLicense);
                String licenseName = spdxListedLicense.getName();
                LICENSE_NAME_MAP.put(licenseName, spdxListedLicense);
                LICENSE_NAMES.add(licenseName);
            } catch (InvalidSPDXAnalysisException e) {
                throw new RuntimeException(e);
            }
        }

        LICENSE_ID_MAP = Collections.unmodifiableMap(LICENSE_ID_MAP);
        LICENSE_NAME_MAP = Collections.unmodifiableMap(LICENSE_NAME_MAP);
        LICENSE_IDS = Collections.unmodifiableList(LICENSE_IDS);
        LICENSE_NAMES.sort(comparing(String::length).reversed().thenComparing(naturalOrder()));
        LICENSE_NAMES = Collections.unmodifiableList(LICENSE_NAMES);

        try {
            DEPRECATED_LICENSE_IDS_MAP = loadLicenseDeprecated();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

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
            Map<String, List<String>> mapping = JSONUtils.loadLicenseMapping(in);
            validateLicenseMapping(mapping);
            return Collections.unmodifiableMap(mapping);
        }
    }

    private static void validateLicenseMapping(Map<String, List<String>> mapping) {
        Set<String> licenseStrings = mapping.keySet();

        for (String licenseString : licenseStrings) {
            if (IDSTRING_PATTERN.matcher(licenseString).matches()) {
                validateSPDXListedLicenseId(licenseString);
                continue;
            }

            validateLicenseString(licenseString);
        }
    }

    private static void validateLicenseString(String licenseString) {
        try {
            AnyLicenseInfo anyLicenseInfo = parseSPDXLicenseString(licenseString);

            if (anyLicenseInfo instanceof SpdxListedLicense) {
                SpdxListedLicense spdxListedLicense = (SpdxListedLicense) anyLicenseInfo;
                String licenseId = spdxListedLicense.getLicenseId();
                validateSPDXListedLicenseId(licenseId);
            }
        } catch (InvalidLicenseStringException e) {
            throw new RuntimeException(e);
        }
    }

    private static void validateSPDXListedLicenseId(String licenseString) {
        if (isUnknownLicenseId(licenseString) || isKnownLicenseId(licenseString)) {
            return;
        }

        throw new RuntimeException("License identifier '" + licenseString + "' is not in list of SPDX licenses");
    }

    /**
     * Returns whether this SPDX license identifier is known.
     *
     * @param licenseId the SPDX license identifier
     */
    public static boolean isKnownLicenseId(String licenseId) {
        return LICENSE_IDS.contains(licenseId);
    }

    /**
     * Returns whether this SPDX license identifier is unknown (<code>NOASSERTION</code> or <code>NONE</code>).
     *
     * @param licenseId the SPDX license identifier
     */
    public static boolean isUnknownLicenseId(String licenseId) {
        return NOASSERTION.equals(licenseId) || NONE.equals(licenseId);
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

    private static void validateLicenseDeprecated(Map<String, String> map) {
        map.forEach((key, value) -> {
            validateLicenseString(key);
            validateLicenseString(value);
        });
    }

    static String normalizeLicenseUrl(String licenseUrl) {
        URI uri = URI.create(licenseUrl).normalize();
        String host = Objects.requireNonNullElse(uri.getHost(), "");
        host = host.replace("www.", "");
        // XXX: Helps match license id
        host = host.replace("creativecommons", "cc");
        host = host.replace('.', '-');
        String path = Objects.requireNonNullElse(uri.getPath(), "");
        path = TWO_DIGIT_PATTERN.matcher(path).replaceAll("$1.$2");
        path = LETTER_DIGIT_PATTERN.matcher(path).replaceAll("$1-$2");
        path = path.replace("cc-0", "cc0");

        for (String extension : EXTENSIONS_TO_REMOVE) {
            path = StringUtils.removeEnd(path, extension);
        }

        path = StringUtils.removeEnd(path, "/");

        return host + path;
    }

    /**
     * Returns whether all the given strings are URLs (contain <code>:/</code> and no whitespace).
     *
     * @param strings the strings
     * @return whether all strings are URLs
     */
    public static boolean isUrl(String... strings) {
        for (String s : strings) {
            if (s == null || StringUtils.containsWhitespace(s) || !s.contains(URL_MARKER)) {
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
     * <li>Replace creativecommons with cc in the host (since CC is used for the SPDX short license identifiers)</li>
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

        Set<Entry<String, List<String>>> entries = mapping.entrySet();

        for (Entry<String, List<String>> entry : entries) {
            String licenseId = entry.getKey();
            List<String> licenseNamesOrUrls = entry.getValue();

            for (String licenseNameOrUrl : licenseNamesOrUrls) {
                if (isUrl(licenseString, licenseNameOrUrl)) {
                    String normalizedLicenseString = normalizeLicenseUrl(licenseString);
                    String normalizedNameOrUrl = normalizeLicenseUrl(licenseNameOrUrl);

                    if (normalizedLicenseString.equals(normalizedNameOrUrl)) {
                        return Optional.of(getCurrentLicenseId(licenseId));
                    }
                } else if (licenseString.equalsIgnoreCase(licenseNameOrUrl)) {
                    return Optional.of(getCurrentLicenseId(licenseId));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Gets the SPDX license identifier using the given mapping.
     *
     * @param mapping the mapping
     * @param name the name
     * @param url the URL
     * @return the matching SPDX license identifier, or <code>NOASSERTION</code> if no match
     */
    public static String getSPDXLicenseId(Map<String, List<String>> mapping, String name, String url) {
        Optional<String> licenseMapping = StringUtils.isBlank(url) ? LicenseUtils.findLicenseMapping(mapping, name)
                : LicenseUtils.findLicenseMapping(mapping, url);
        return licenseMapping.or(() -> LicenseUtils.findMatchingLicense(name, url)).orElse(NOASSERTION);
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

    static AnyLicenseInfo parseSPDXLicenseString(String licenseString) throws InvalidLicenseStringException {
        return LicenseInfoFactory.parseSPDXLicenseString(licenseString, null, null, null);
    }

    public static int getNumberOfSPDXLicenses() {
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
        return Optional.ofNullable(license != null ? getCurrentLicenseId(license.getLicenseId()) : null);
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
     * @return the license URL (which may be <code>null</code>)
     */
    public static Optional<String> findMatchingLicenseName(String licenseName, String licenseUrl) {
        for (String licenseId : LICENSE_IDS) {
            if (containsWordsInSameOrder(licenseName, StringUtils.replace(licenseId, "-only", ""))
                    || containsWordsInSameOrder(licenseUrl, licenseId)) {
                return Optional.of(getCurrentLicenseId(licenseId));
            }
        }

        for (String spdxLicenseName : LICENSE_NAMES) {
            if (spdxLicenseName.equalsIgnoreCase(licenseName)
                    || containsWordsInSameOrder(licenseName, StringUtils.replace(spdxLicenseName, " only", ""))
                    || containsWordsInSameOrder(licenseUrl, spdxLicenseName)) {
                SpdxListedLicense spdxListedLicense = LICENSE_NAME_MAP.get(spdxLicenseName);
                String licenseId = getCurrentLicenseId(spdxListedLicense.getLicenseId());
                return Optional.of(licenseId);
            }
        }

        return Optional.empty();
    }

    static List<String> tokenizeLicenseString(String licenseString) {
        String newLicenseString = licenseString;

        if (isUrl(newLicenseString)) {
            newLicenseString = normalizeLicenseUrl(newLicenseString);
            newLicenseString = StringUtils.replaceChars(newLicenseString, "/", "-");
        }

        newLicenseString = SINGLE_DIGIT_PATTERN.matcher(newLicenseString).replaceAll("${b}${major}.0${a}");
        newLicenseString = StringUtils.replaceChars(newLicenseString, ".-", "_ ");
        newLicenseString = PUNCT_PATTERN.matcher(newLicenseString).replaceAll("");
        newLicenseString = NAME_VERSION_PATTERN.matcher(newLicenseString).replaceAll("${name} ${major} ${minor}");
        newLicenseString = newLicenseString.toLowerCase(Locale.ROOT);
        String[] split = WHITESPACE_PATTERN.split(newLicenseString);
        List<String> list = new ArrayList<>(Arrays.asList(split));
        list.remove("v");
        return Collections.unmodifiableList(list);
    }

    static boolean containsWordsInSameOrder(String licenseStringCandidate, String licenseString) {
        if (licenseStringCandidate == null || licenseString == null) {
            return false;
        }

        String[] licenseCandidates = tokenizeLicenseString(licenseStringCandidate).toArray(new String[0]);
        String[] searchStrings = tokenizeLicenseString(licenseString).toArray(new String[0]);
        int startIndex = 0;

        for (String objectToFind : searchStrings) {
            int index = ArrayUtils.indexOf(licenseCandidates, objectToFind, startIndex);

            if (index == -1) {
                return false;
            }

            startIndex = index + 1;
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
                        .collect(Collectors.toUnmodifiableList());

                if (seeAlso.contains(normalizeLicenseUrl(licenseUrl))) {
                    return Optional.of(getCurrentLicenseId(license.getLicenseId()));
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

    /**
     * Returns whether the given file object is <code>META-INF/MANIFEST.MF</code>.
     *
     * @param fileObject the file object
     * @return whether the given file object is <code>META-INF/MANIFEST.MF</code>
     */
    public static boolean isManifestMfFileName(FileObject fileObject) {
        return MANIFEST_MF_PATTERN.matcher(fileObject.getName().getPath()).matches();
    }

    /**
     * Returns whether the given file object is a license text file. Matches files such as
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
    public static boolean isLicenseFileName(FileObject fileObject) {
        FileName name = fileObject.getName();
        String fileName = name.getBaseName();

        if (LICENSE_FILE_PATTERN.matcher(fileName).matches()) {
            return true;
        }

        String extension = FilenameUtils.getExtension(fileName);

        if (StringUtils.equalsAny(extension, "", "txt")) {
            String baseName = StringUtils.removeEnd(fileName, extension);
            return LicenseUtils.isKnownLicenseId(baseName);
        }

        return false;
    }

    /**
     * Finds a matching SPDX license identifier for the text in the given license text file, if any.
     *
     * @param licenseFileObject the license text file
     * @return the matching license identifier, if any
     */
    public static Optional<String> findMatchingLicense(FileObject licenseFileObject) {
        try (FileContent fc = licenseFileObject.getContent(); InputStream in = fc.getInputStream()) {
            String licenseText = new String(in.readAllBytes(), UTF_8);
            return LICENSE_ID_TEXT_LIST.stream()
                    .map(id -> LICENSE_ID_MAP.get(id))
                    .map(license -> findMatchingSPDXLicenseIdentifier(license, licenseText))
                    .flatMap(Optional::stream)
                    .findAny()
                    .or(() -> findMatchingSPDXLicenseIdentifierOrLicense(licenseText));
        } catch (IOException e) {
            return Optional.empty();
        }
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

    static String licenseFileToText(Path path) {
        try (Stream<String> stream = Files.lines(path)) {
            return stream.limit(LINE_LIMIT).map(String::trim).collect(Collectors.joining(SPACE));
        } catch (IOException e) {
            return EMPTY;
        }
    }

    static String licenseFileToText(String text) {
        return text.lines().limit(LINE_LIMIT).map(String::trim).collect(Collectors.joining(SPACE));
    }

    static Optional<String> findMatchingSPDXLicenseIdentifierOrLicense(String text) {
        String lines = licenseFileToText(text);
        return findMatchingSPDXLicenseIdentifier(lines).or(() -> findMatchingLicense(lines, null));
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

    static Optional<String> findMatchingSPDXLicenseIdentifier(SpdxListedLicense license, String licenseText) {
        try {
            if (!LicenseCompareHelper.isTextStandardLicense(license, licenseText).isDifferenceFound()) {
                String licenseId = license.getLicenseId();
                return Optional.of(getCurrentLicenseId(licenseId));
            }
        } catch (SpdxCompareException | InvalidSPDXAnalysisException e) {
            // ignore
        }

        return Optional.empty();
    }

    static List<BundleLicense> getBundleLicenseFromManifest(String bundleLicense) throws IOException {
        return BundleLicense.parse(bundleLicense);
    }

    /**
     * Returns the list of licenses from the <code>Bundle-License</code> manifest header, if any.
     *
     * @param manifestFileObject the file object
     * @return the list of licenses from the <code>Bundle-License</code> manifest header, if any
     * @throws IOException if an error occurs reading from the file object
     */
    public static List<BundleLicense> getBundleLicenseFromManifest(FileObject manifestFileObject) throws IOException {
        try (FileContent fc = manifestFileObject.getContent(); InputStream in = fc.getInputStream()) {
            Manifest manifest = new Manifest(in);
            Attributes mainAttributes = manifest.getMainAttributes();
            String bundleLicense = mainAttributes.getValue(BUNDLE_LICENSE);
            return getBundleLicenseFromManifest(bundleLicense);
        }
    }

    static String getCurrentLicenseId(String licenseId) {
        String currentId = DEPRECATED_LICENSE_IDS_MAP.get(licenseId);
        return currentId != null ? currentId : licenseId;
    }
}
