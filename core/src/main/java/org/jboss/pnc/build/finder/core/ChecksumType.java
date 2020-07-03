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

public enum ChecksumType {
    md5(0, "MD5"), sha1(1, "SHA-1"), sha256(2, "SHA-256");

    private final Integer value;

    private final String algorithm;

    ChecksumType(int value, String algorithm) {
        this.value = value;
        this.algorithm = algorithm;
    }

    public static ChecksumType fromInteger(Integer value) {
        for (ChecksumType checksum : values()) {
            if (value.equals(checksum.getValue())) {
                return checksum;
            }
        }

        throw new IllegalArgumentException("Unknown value for checksum type: " + value);
    }

    public Integer getValue() {
        return value;
    }

    public String getAlgorithm() {
        return algorithm;
    }
}
