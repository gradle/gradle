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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import static org.gradle.internal.snapshot.ChildMapFactory.childMap;

public class SingletonChildMap<T> implements ChildMap<T> {
    private final Entry<T> entry;

    public SingletonChildMap(String path, T child) {
        this(new Entry<>(path, child));
    }

    public SingletonChildMap(Entry<T> entry) {
        this.entry = entry;
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
    public List<Entry<T>> entries() {
        return Collections.singletonList(entry);
    }

    @Override
    public <R> R getNode(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, GetNodeHandler<T, R> handler) {
        return entry.getNode(targetPath, caseSensitivity, handler);
    }

    @Override
    public <RESULT> ChildMap<RESULT> invalidate(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, InvalidationHandler<T, RESULT> handler) {
        return entry.getNode(targetPath, caseSensitivity, new GetNodeHandler<T, ChildMap<RESULT>>() {
            @Override
            public ChildMap<RESULT> handleAsDescendantOfChild(VfsRelativePath pathInChild, T child) {
                Optional<RESULT> invalidatedChild = handler.handleAsDescendantOfChild(pathInChild, child);
                return invalidatedChild
                    .map(it -> withReplacedChild(entry.getPath(), it))
                    .orElseGet(EmptyChildMap::getInstance);
            }

            @Override
            public ChildMap<RESULT> handleExactMatchWithChild(T child) {
                handler.handleExactMatchWithChild(child);
                return EmptyChildMap.getInstance();
            }

            @Override
            public ChildMap<RESULT> handleUnrelatedToAnyChild() {
                handler.handleUnrelatedToAnyChild();
                return castThis();
            }

            @Override
            public ChildMap<RESULT> handleAsAncestorOfChild(String childPath, T child) {
                handler.handleAsAncestorOfChild(childPath, child);
                return EmptyChildMap.getInstance();
            }
        });
    }

    @Override
    public ChildMap<T> store(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, StoreHandler<T> storeHandler) {
        return entry.handlePath(targetPath, 0, caseSensitivity, new AbstractListChildMap.PathRelationshipHandler<ChildMap<T>>() {
            @Override
            public ChildMap<T> handleAsDescendantOfChild(VfsRelativePath targetPath, String childPath, int childIndex) {
                T oldChild = entry.getValue();
                T newChild = storeHandler.handleAsDescendantOfChild(targetPath.fromChild(childPath), oldChild);
                return withReplacedChild(childPath, newChild);
            }

            @Override
            public ChildMap<T> handleAsAncestorOfChild(VfsRelativePath targetPath, String childPath, int childIndex) {
                T newChild = storeHandler.handleAsAncestorOfChild(childPath, entry.getValue());
                return withReplacedChild(targetPath.getAsString(), newChild);
            }

            @Override
            public ChildMap<T> handleExactMatchWithChild(VfsRelativePath targetPath, String childPath, int childIndex) {
                T newChild = storeHandler.mergeWithExisting(entry.getValue());
                return withReplacedChild(childPath, newChild);
            }

            @Override
            public ChildMap<T> handleSiblingOfChild(VfsRelativePath targetPath, String childPath, int childIndex, int commonPrefixLength) {
                T oldChild = entry.getValue();
                String commonPrefix = childPath.substring(0, commonPrefixLength);
                String newChildPath = childPath.substring(commonPrefixLength + 1);
                Entry<T> newChild = new Entry<>(newChildPath, oldChild);
                String siblingPath = targetPath.suffixStartingFrom(commonPrefixLength + 1).getAsString();
                Entry<T> sibling = new Entry<>(siblingPath, storeHandler.createChild());
                ChildMap<T> newChildren = childMap(caseSensitivity, newChild, sibling);
                return withReplacedChild(commonPrefix, storeHandler.createNodeFromChildren(newChildren));
            }

            @Override
            public ChildMap<T> handleUnrelatedToAnyChild(VfsRelativePath targetPath, int indexOfNextBiggerChild) {
                String path = targetPath.getAsString();
                T newNode = storeHandler.createChild();
                return withNewChild(caseSensitivity, path, newNode);
            }
        });
    }

    private <RESULT> ChildMap<RESULT> withNewChild(CaseSensitivity caseSensitivity, String newChildPath, RESULT newChild) {
        @SuppressWarnings("unchecked")
        Entry<RESULT> currentEntry = (Entry<RESULT>) entry;
        Entry<RESULT> newEntry = new Entry<>(newChildPath, newChild);
        return childMap(caseSensitivity, currentEntry, newEntry);
    }

    private <RESULT> ChildMap<RESULT> withReplacedChild(String newPath, RESULT newChild) {
        if (entry.getPath().equals(newPath) && entry.getValue().equals(newChild)) {
            return castThis();
        }
        return new SingletonChildMap<>(newPath, newChild);
    }

    @SuppressWarnings("unchecked")
    private <RESULT> SingletonChildMap<RESULT> castThis() {
        return (SingletonChildMap<RESULT>) this;
    }

    @Override
    public void visitChildren(BiConsumer<String, T> visitor) {
        visitor.accept(entry.getPath(), entry.getValue());
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
