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
package org.jboss.pnc.build.finder.koji;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.jboss.pnc.build.finder.core.BuildFinderObjectMapper;
import org.jboss.pnc.build.finder.core.BuildSystemInteger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class KojiJSONUtils {
    private static final ObjectMapper MAPPER = new BuildFinderObjectMapper();

    private KojiJSONUtils() {

    }

    private static class MapTypeReference extends TypeReference<Map<BuildSystemInteger, KojiBuild>> {
        MapTypeReference() {

        }
    }

    public static <T> T readValue(String content, Class<T> valueType) throws JsonProcessingException {
        return MAPPER.readValue(content, valueType);
    }

    public static String writeValueAsString(Object value) throws JsonProcessingException {
        return MAPPER.writeValueAsString(value);
    }

    public static Map<BuildSystemInteger, KojiBuild> loadBuildsFile(File file) throws IOException {
        TypeReference<Map<BuildSystemInteger, KojiBuild>> typeReference = new MapTypeReference();

        return MAPPER.readValue(file, typeReference);
    }

}
