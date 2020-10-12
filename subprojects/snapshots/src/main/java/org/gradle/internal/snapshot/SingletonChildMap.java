/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.snapshot;

import com.google.common.collect.ImmutableList;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

public class SingletonChildMap<T> implements ChildMap<T> {
    private final Entry<T> entry;

    public SingletonChildMap(String path, T child) {
        this(new Entry<>(path, child));
    }

    public SingletonChildMap(Entry<T> entry) {
        this.entry = entry;
    }

    @Override
    public Optional<T> get(VfsRelativePath relativePath) {
        return Optional.empty();
    }

    @Override
    public <R> R handlePath(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, PathRelationshipHandler<R> handler) {
        return entry.handlePath(relativePath, 0, caseSensitivity, handler);
    }

    @Override
    public T get(int index) {
        checkIndex(index);
        return entry.getValue();
    }

    @Override
    public int indexOf(String path, CaseSensitivity caseSensitivity) {
        return (entry.getPath().equals(path)) ? 0 : -1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public List<T> values() {
        return Collections.singletonList(entry.getValue());
    }

    @Override
    public ChildMap<T> withNewChild(int insertBefore, String path, T newChild) {
        Entry<T> newEntry = new Entry<>(path, newChild);
        List<Entry<T>> newChildren = insertBefore == 0
            ? ImmutableList.of(newEntry, entry)
            : ImmutableList.of(entry, newEntry);
        return new DefaultChildMap<>(newChildren);
    }

    @Override
    public ChildMap<T> withReplacedChild(int childIndex, String newPath, T newChild) {
        checkIndex(childIndex);
        if (entry.getPath().equals(newPath) && entry.getValue().equals(newChild)) {
            return this;
        }
        return new SingletonChildMap<>(newPath, newChild);
    }

    @Override
    public ChildMap<T> withRemovedChild(int childIndex) {
        checkIndex(childIndex);
        return EmptyChildMap.getInstance();
    }

    @Override
    public void visitChildren(BiConsumer<String, T> visitor) {
        visitor.accept(entry.getPath(), entry.getValue());
    }

    private void checkIndex(int childIndex) {
        if (childIndex != 0) {
            throw new IndexOutOfBoundsException("Index out of range: " + childIndex);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SingletonChildMap<?> that = (SingletonChildMap<?>) o;

        return entry.equals(that.entry);
    }

    @Override
    public int hashCode() {
        return entry.hashCode();
    }
}
