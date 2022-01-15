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
package org.jboss.pnc.build.finder.pnc.client;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

import org.jboss.pnc.client.RemoteCollection;

/**
 * Static implementation of RemoteCollection, which loads all the remote content and stores it inside the entity
 *
 * @author Jakub Bartecek
 */
public class StaticRemoteCollection<T> implements Collection<T>, RemoteCollection<T> {
    private final Collection<T> staticCollection;

    public StaticRemoteCollection(RemoteCollection<T> remoteCollection) {
        this.staticCollection = Collections.unmodifiableCollection(remoteCollection.getAll());
    }

    public StaticRemoteCollection(Collection<T> collection) {
        this.staticCollection = Collections.unmodifiableCollection(collection);
    }

    @Override
    public int size() {
        return staticCollection.size();
    }

    @Override
    public boolean isEmpty() {
        return staticCollection.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return staticCollection.contains(o);
    }

    @Override
    public Collection<T> getAll() {
        return staticCollection;
    }

    @Override
    public Iterator<T> iterator() {
        return staticCollection.iterator();
    }

    @Override
    public Object[] toArray() {
        return staticCollection.toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return staticCollection.toArray(a);
    }

    @Override
    public boolean add(T t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return staticCollection.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        StaticRemoteCollection<?> that = (StaticRemoteCollection<?>) o;
        return Objects.equals(staticCollection, that.staticCollection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(staticCollection);
    }

    @Override
    public String toString() {
        return staticCollection.toString();
    }
}
