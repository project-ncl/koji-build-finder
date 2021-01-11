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
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

import org.infinispan.commons.marshall.AdvancedExternalizer;
import org.infinispan.commons.marshall.SerializeWith;
import org.infinispan.commons.util.Util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize
@SerializeWith(LocalFile.LocalFileExternalizer.class)
public class LocalFile {
    private final String filename;

    private final long size;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public LocalFile(@JsonProperty("filename") String filename, @JsonProperty("size") long size) {
        this.filename = filename;
        this.size = size;
    }

    public String getFilename() {
        return filename;
    }

    public long getSize() {
        return size;
    }

    @Override
    public String toString() {
        return "LocalFile{" + "filename='" + filename + '\'' + ", size=" + size + '}';
    }

    public static class LocalFileExternalizer implements AdvancedExternalizer<LocalFile> {
        private static final long serialVersionUID = -3322870654564600039L;

        private static final Integer ID = (Character.getNumericValue('L') << 16) | (Character.getNumericValue('F') << 8)
                | Character.getNumericValue('E');

        private static final int VERSION = 1;

        @Override
        public void writeObject(ObjectOutput output, LocalFile object) throws IOException {
            output.writeInt(VERSION);
            output.writeObject(object.getFilename());
            output.writeLong(object.getSize());
        }

        @Override
        public LocalFile readObject(ObjectInput input) throws IOException, ClassNotFoundException {
            int version = input.readInt();

            if (version != 1) {
                throw new IOException("Invalid version: " + version);
            }

            String filename = (String) input.readObject();
            long size = input.readLong();

            return new LocalFile(filename, size);
        }

        @Override
        public Set<Class<? extends LocalFile>> getTypeClasses() {
            return Util.asSet(LocalFile.class);
        }

        @Override
        public Integer getId() {
            return ID;
        }
    }
}
