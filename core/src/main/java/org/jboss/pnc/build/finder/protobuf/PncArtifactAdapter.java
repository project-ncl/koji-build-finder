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
package org.jboss.pnc.build.finder.protobuf;

import org.infinispan.protostream.annotations.ProtoAdapter;
import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;
import org.jboss.pnc.build.finder.koji.KojiJSONUtils;
import org.jboss.pnc.dto.Artifact;

import com.fasterxml.jackson.core.JsonProcessingException;

@ProtoAdapter(Artifact.class)
public class PncArtifactAdapter {
    @ProtoFactory
    Artifact create(String jsonData) {
        try {
            return KojiJSONUtils.readValue(jsonData, Artifact.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @ProtoField(number = 1)
    String getJsonData(Artifact artifact) {
        try {
            return KojiJSONUtils.writeValueAsString(artifact);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
