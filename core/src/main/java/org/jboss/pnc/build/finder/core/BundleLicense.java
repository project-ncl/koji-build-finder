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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class BundleLicense {
    private static final String EXTERNAL = "<<EXTERNAL>>";

    private static final String LINK = "link";

    private static final String DESCRIPTION = "description";

    private static final Pattern LICENSE_LIST_PATTERN = Pattern.compile("\\s*,\\s*(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

    private static final Pattern LICENSE_PATTERN = Pattern.compile("\\s*;\\s*");

    private static final Pattern LICENSE_ATTRIBUTE_PATTERN = Pattern.compile("\\s*=\\s*");

    private String licenseIdentifier;

    private String link;

    private String description;

    public BundleLicense() {

    }

    public String getLicenseIdentifier() {
        return licenseIdentifier;
    }

    public void setLicenseIdentifier(String licenseIdentifier) {
        this.licenseIdentifier = licenseIdentifier;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BundleLicense that = (BundleLicense) o;
        return Objects.equals(licenseIdentifier, that.licenseIdentifier) && Objects.equals(link, that.link)
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(licenseIdentifier, link, description);
    }

    @Override
    public String toString() {
        return "BundleLicense: " + "licenseIdentifier: '" + licenseIdentifier + '\'' + ", link: '" + link + '\''
                + ", description: '" + description + '\'';
    }

    public static List<BundleLicense> parse(String s) throws IOException {
        if (StringUtils.isEmpty(s) || EXTERNAL.equals(s)) {
            return List.of();
        }

        List<BundleLicense> list = new ArrayList<>(3);
        String[] split = LICENSE_LIST_PATTERN.split(s);

        for (String string : split) {
            BundleLicense bundleLicense = newBundleLicense(string);
            list.add(bundleLicense);
        }

        return Collections.unmodifiableList(list);
    }

    private static BundleLicense newBundleLicense(String value) throws IOException {
        BundleLicense bundleLicense = new BundleLicense();
        String[] licenseTokens = LICENSE_PATTERN.split(value, 2);
        String licenseIdentifier = removeQuotes(licenseTokens[0]);

        if (!LicenseUtils.isUrl(licenseIdentifier)) {
            bundleLicense.setLicenseIdentifier(licenseIdentifier);
        } else {
            bundleLicense.setLink(licenseIdentifier);
        }

        if (licenseTokens.length == 2) {
            String[] attributes = LICENSE_PATTERN.split(removeQuotes(licenseTokens[1]));

            for (String attribute : attributes) {
                String[] kv = LICENSE_ATTRIBUTE_PATTERN.split(attribute, 2);

                if (kv.length != 2) {
                    throw new IOException("Expected key=value pair, but got " + attribute);
                }

                String k = removeQuotes(kv[0]);
                String v = removeQuotes(kv[1]);

                switch (k) {
                    case LINK -> {
                        if (!LicenseUtils.isUrl(v)) {
                            throw new IOException("Expected URL, but got " + v);
                        }

                        bundleLicense.setLink(v);
                    }
                    case DESCRIPTION -> bundleLicense.setDescription(v);
                    default -> throw new IOException("Unknown key " + k);
                }
            }
        }

        return bundleLicense;
    }

    private static String removeQuotes(String s) {
        int length = s.length();

        if (length < 2) {
            return s;
        }

        int endIndex = length - 1;
        return s.charAt(0) == '"' && s.charAt(endIndex) == '"' ? s.substring(1, endIndex) : s;
    }
}
