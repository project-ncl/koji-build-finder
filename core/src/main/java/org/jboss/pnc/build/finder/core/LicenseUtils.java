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

import static org.apache.commons.lang3.ArrayUtils.EMPTY_STRING_ARRAY;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;

/**
 * Utilities for working with SPDX licenses.
 */
public final class LicenseUtils {
    private static final Pattern PUNCT_PATTERN = Pattern.compile("\\p{Punct}");

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final Pattern TWO_DIGIT_PATTERN = Pattern.compile("([0-9])([0-9])");

    private static final Pattern LETTER_DIGIT_PATTERN = Pattern.compile("([A-Za-z])([0-9])");

    private static final Pattern MANIFEST_MF_PATTERN = Pattern.compile("^.*META-INF/MANIFEST.MF$");

    private static final String URL_MARKER = ":/";

    private static final String DOT = ".";

    private static final List<String> TEXT_EXTENSIONS = List.of(".html", ".md", ".php", ".txt");

    private static final Pattern NAME_VERSION_PATTERN = Pattern
            .compile("(?<name>[A-Z-a-z])[Vv]?(?<major>[1-9]+)(\\.(?<minor>[0-9]+))?");

    private static final Pattern SINGLE_DIGIT_PATTERN = Pattern.compile("(?<b>[^0-9.])(?<major>[1-9])(?<a>[^0-9.])");

    private static final int LINE_LIMIT = 5;

    private static final String BUNDLE_LICENSE = "Bundle-License";

    private LicenseUtils() {
        throw new IllegalArgumentException("This is a utility class and cannot be instantiated");
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

        for (String extension : TEXT_EXTENSIONS) {
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
            if (s == null || !s.contains(URL_MARKER) || StringUtils.containsWhitespace(s)) {
                return false;
            }
        }

        return true;
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

        String[] licenseCandidates = tokenizeLicenseString(licenseStringCandidate).toArray(EMPTY_STRING_ARRAY);
        String[] searchStrings = tokenizeLicenseString(licenseString).toArray(EMPTY_STRING_ARRAY);
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
     * Returns whether the given file object is <code>META-INF/MANIFEST.MF</code>.
     *
     * @param fileObject the file object
     * @return whether the given file object is <code>META-INF/MANIFEST.MF</code>
     */
    public static boolean isManifestMfFileName(FileObject fileObject) {
        return MANIFEST_MF_PATTERN.matcher(fileObject.getName().getPath()).matches();
    }

    /**
     * Returns whether this extension is a plain-text file extension.
     *
     * @param extension the extension
     * @return whether this extension is a plain-text file extension
     */
    public static boolean isTextExtension(String extension) {
        if (EMPTY.equals(extension)) {
            return true;
        }

        String ext = StringUtils.prependIfMissing(extension, DOT);
        return TEXT_EXTENSIONS.contains(ext);
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

    static List<BundleLicense> getBundleLicenseFromManifest(String bundleLicense) throws IOException {
        return BundleLicense.parse(bundleLicense);
    }

    /**
     * Returns the list of licenses from the <code>Bundle-License</code> manifest header, if any.
     *
     * @param manifestFileObject the file object
     * @return the list of licenses from the <code>Bundle-License</code> manifest header, if any
     * @throws IOException if an error occurs while reading from the file object
     */
    public static List<BundleLicense> getBundleLicenseFromManifest(FileObject manifestFileObject) throws IOException {
        try (FileContent fc = manifestFileObject.getContent(); InputStream in = fc.getInputStream()) {
            Manifest manifest = new Manifest(in);
            Attributes mainAttributes = manifest.getMainAttributes();
            String bundleLicense = mainAttributes.getValue(BUNDLE_LICENSE);
            return getBundleLicenseFromManifest(bundleLicense);
        }
    }

    /**
     * Gets the first non-blank string.
     *
     * @param strings the strings
     * @return the first non-blank string. or null
     */
    public static String getFirstNonBlankString(String... strings) {
        return findFirstNonBlankString(strings).orElse(null);
    }

    /**
     * Finds the first non-blank string.
     *
     * @param strings the strings
     * @return the first non-blank string. or empty.
     */
    public static Optional<String> findFirstNonBlankString(String... strings) {
        for (String string : strings) {
            if (!StringUtils.isBlank(string)) {
                return Optional.of(string);
            }
        }

        return Optional.empty();
    }
}
