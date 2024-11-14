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

import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize
public class LocalFile {
    private final String filename;

    private final long size;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    @ProtoFactory
    public LocalFile(@JsonProperty("filename") String filename, @JsonProperty("size") long size) {
        this.filename = filename;
        this.size = size;
    }

    @ProtoField(value = 1)
    public String getFilename() {
        return filename;
    }

    @ProtoField(value = 2, defaultValue = "0")
    public long getSize() {
        return size;
    }

    @Override
    public String toString() {
        return "LocalFile{" + "filename='" + filename + '\'' + ", size=" + size + '}';
    }
}
