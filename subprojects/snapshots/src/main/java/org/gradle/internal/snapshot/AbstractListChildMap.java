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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public abstract class AbstractListChildMap<T> extends AbstractChildMap<T> {
    protected final List<Entry<T>> children;

    protected AbstractListChildMap(List<Entry<T>> children) {
        this.children = children;
    }

    @Nullable
    protected abstract Entry<T> findEntryWithPrefix(VfsRelativePath relativePath, CaseSensitivity caseSensitivity);

    protected int findChildIndexWithCommonPrefix(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return SearchUtil.binarySearch(
            children,
            candidate -> relativePath.compareToFirstSegment(candidate.getPath(), caseSensitivity)
        );
    }

    @Override
    public <R> R findChild(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, FindChildHandler<T, R> handler) {
        Entry<T> entry = findEntryWithPrefix(relativePath, caseSensitivity);
        if (entry == null) {
            return handler.handleNotFound();
        }
        return relativePath.length() == entry.getPath().length()
            ? handler.getFromChild(entry.getValue())
            : handler.findInChild(relativePath.fromChild(entry.getPath()), entry.getValue());
    }

    @Override
    protected <R> R handlePath(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, PathRelationshipHandler<R> handler) {
        int childIndex = findChildIndexWithCommonPrefix(relativePath, caseSensitivity);
        if (childIndex >= 0) {
            return children.get(childIndex).handlePath(relativePath, childIndex, caseSensitivity, handler);
        }
        return handler.handleDifferent(-childIndex - 1);
    }

    @Override
    protected T get(int index) {
        return children.get(index).getValue();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public List<T> values() {
        return children.stream()
            .map(Entry::getValue)
            .collect(Collectors.toList());
    }

    @Override
    protected AbstractChildMap<T> withNewChild(int insertBefore, String path, T newChild) {
        List<Entry<T>> newChildren = new ArrayList<>(children);
        newChildren.add(insertBefore, new Entry<>(path, newChild));
        return childMap(newChildren);
    }

    @Override
    protected AbstractChildMap<T> withReplacedChild(int childIndex, String newPath, T newChild) {
        Entry<T> oldEntry = children.get(childIndex);
        if (oldEntry.getPath().equals(newPath) && oldEntry.getValue().equals(newChild)) {
            return this;
        }
        List<Entry<T>> newChildren = new ArrayList<>(children);
        newChildren.set(childIndex, new Entry<>(newPath, newChild));
        return childMap(newChildren);
    }

    @Override
    protected AbstractChildMap<T> withRemovedChild(int childIndex) {
        List<Entry<T>> newChildren = new ArrayList<>(children);
        newChildren.remove(childIndex);
        return childMap(newChildren);
    }

    @Override
    public void visitChildren(BiConsumer<String, T> visitor) {
        for (Entry<T> child : children) {
            visitor.accept(child.getPath(), child.getValue());
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

        AbstractListChildMap<?> that = (AbstractListChildMap<?>) o;

        return children.equals(that.children);
    }

    @Override
    public int hashCode() {
        return children.hashCode();
    }
}
