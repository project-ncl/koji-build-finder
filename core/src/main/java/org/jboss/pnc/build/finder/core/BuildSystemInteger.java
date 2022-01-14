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

import java.util.Objects;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;

public class BuildSystemInteger implements Comparable<Object> {
    private final String value;

    private final BuildSystem buildSystem;

    public BuildSystemInteger(int value) {
        this.value = String.valueOf(value);
        this.buildSystem = BuildSystem.none;
    }

    public BuildSystemInteger(int value, BuildSystem buildSystem) {
        this.value = String.valueOf(value);
        this.buildSystem = buildSystem;
    }

    public BuildSystemInteger(String value, BuildSystem buildSystem) {
        this.value = value;
        this.buildSystem = buildSystem;
    }

    public String getValue() {
        return value;
    }

    public BuildSystem getBuildSystem() {
        return buildSystem;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BuildSystemInteger that = (BuildSystemInteger) o;
        return Objects.equals(value, that.value) && buildSystem == that.buildSystem;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, buildSystem);
    }

    @Override
    public String toString() {
        String result;

        if (buildSystem == null) {
            result = String.valueOf(value);
        } else {
            result = value + ", " + buildSystem;
        }

        return result;
    }

    @Override
    public int compareTo(Object o) {
        BuildSystemInteger other = (BuildSystemInteger) o;
        int result;

        if (buildSystem == other.getBuildSystem()) {
            result = value.compareTo(other.getValue());
        } else {
            result = buildSystem.compareTo(other.getBuildSystem());
        }

        return result;
    }

    public static class Deserializer extends KeyDeserializer {
        private static final Pattern PATTERN = Pattern.compile(", ");

        @Override
        public Object deserializeKey(String s, DeserializationContext deserializationContext) {
            String[] t = PATTERN.split(s);
            String value = t[0];
            BuildSystem buildSystem = BuildSystem.valueOf(t[1]);
            return new BuildSystemInteger(value, buildSystem);
        }
    }
}
