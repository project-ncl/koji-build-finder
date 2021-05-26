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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JSONUtils {
    private static final ObjectMapper MAPPER = new BuildFinderObjectMapper();

    private JSONUtils() {

    }

    public static String dumpString(Object obj) throws JsonProcessingException {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    }

    public static void dumpObjectToFile(Object obj, File file) throws IOException {
        dumpObjectToFile(obj, file, MAPPER);
    }

    public static void dumpObjectToFile(Object obj, File file, ObjectMapper mapper) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, obj);
        Files.write(file.toPath(), Collections.singletonList(""), StandardOpenOption.APPEND);
    }

    public static Map<String, Collection<LocalFile>> loadChecksumsFile(File file) throws IOException {
        TypeReference<Map<String, Collection<LocalFile>>> typeReference = new MapTypeReference();
        return MAPPER.readValue(file, typeReference);
    }

    private static class MapTypeReference extends TypeReference<Map<String, Collection<LocalFile>>> {
        MapTypeReference() {

        }
    }
}
