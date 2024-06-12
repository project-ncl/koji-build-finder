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

import java.util.Collection;
import java.util.stream.Collectors;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.infinispan.protostream.WrappedMessage;
import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;

/**
 * Class wrapping around MultiValuedMap interface (via HashSetValuedHashMap) so that it can be marshalled with Protobuf.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
public class MultiValuedMapProtobufWrapper<K, V> extends HashSetValuedHashMap<K, V> {
    private static final long serialVersionUID = -4929571021288563518L;

    /**
     * Constructor wrapping around the MultiValuedMap.
     *
     * @param map MultiValuedMap to wrap around
     */
    public MultiValuedMapProtobufWrapper(MultiValuedMap<K, V> map) {
        super(map);
    }

    /**
     * This method is called for Protobuf to MultiValuedMapWrapper convertor.
     *
     * @param entries the entries
     */
    @ProtoFactory
    MultiValuedMapProtobufWrapper(Collection<MultiValuedMapProtobufEntry<K, V>> entries) {
        super();
        entries.forEach(a -> this.putAll(a.key, a.values));
    }

    @ProtoField(number = 1)
    Collection<MultiValuedMapProtobufEntry<K, V>> getEntries() {
        return this.asMap()
                .entrySet()
                .stream()
                .map(a -> new MultiValuedMapProtobufEntry<>(a.getKey(), a.getValue()))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * POJO representing the key and list of values of MultiValuedMap for Protobuf.
     * <p>
     * The key and values are marshalled as a WrappedMessage. In theory, the WrappedMessage object should know how to
     * marshall primitives, enums, and other POJO objects which can be marshalled by Protobuf (as long as they are in
     * the `AutoProtoSchemaBuilder` list in the ProtobufSerializer interface).
     *
     * @param <K> Key type
     * @param <V> Value type
     *
     * @see ProtobufSerializer
     */
    static class MultiValuedMapProtobufEntry<K, V> {
        private K key;

        private Collection<V> values;

        // Required by Protostream
        MultiValuedMapProtobufEntry() {

        }

        MultiValuedMapProtobufEntry(K key, Collection<V> values) {
            this.key = key;
            this.values = values;
        }

        @SuppressWarnings("unchecked")
        @ProtoFactory
        MultiValuedMapProtobufEntry(WrappedMessage key, Collection<WrappedMessage> values) {
            this.key = (K) key.getValue();
            this.values = values.stream().map(a -> (V) a.getValue()).collect(Collectors.toUnmodifiableList());
        }

        @ProtoField(number = 1)
        WrappedMessage getKey() {
            return new WrappedMessage(key);
        }

        @ProtoField(number = 2)
        Collection<WrappedMessage> getValues() {
            return this.values.stream().map(WrappedMessage::new).collect(Collectors.toUnmodifiableList());
        }
    }
}
