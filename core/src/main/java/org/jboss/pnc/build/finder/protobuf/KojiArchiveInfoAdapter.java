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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;

/**
 * Protostream adapter to be able to properly marshall/unmarshall
 * {@link com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo}.
 * <p/>
 * This class is defined in Kojiji and can't be easily modified to add Proto annotations. Instead, the marshalling
 * process involves converting KojiArchiveInfo object into JSON and stored in Protobuf as a string. The unmarshalling
 * process involves reading the JSON string from Protobuf and converting it back to a KojiArchiveInfo object.
 */
@ProtoAdapter(KojiArchiveInfo.class)
public class KojiArchiveInfoAdapter {
    @ProtoFactory
    KojiArchiveInfo create(String jsonData) {
        try {
            return KojiJSONUtils.readValue(jsonData, KojiArchiveInfo.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @ProtoField(1)
    String getJsonData(KojiArchiveInfo kojiArchiveInfo) {
        try {
            return KojiJSONUtils.writeValueAsString(kojiArchiveInfo);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
