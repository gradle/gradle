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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public abstract class AbstractListChildMap<T> implements ChildMap<T> {
    protected final List<Entry<T>> entries;

    protected AbstractListChildMap(List<Entry<T>> entries) {
        this.entries = entries;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public <RESULT> RESULT withNode(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, NodeHandler<T, RESULT> handler) {
        return null;
    }

    @Override
    public Stream<Entry<T>> stream() {
        return entries.stream();
    }

    protected int findChildIndexWithCommonPrefix(VfsRelativePath targetPath, CaseSensitivity caseSensitivity) {
        return SearchUtil.binarySearch(
            entries,
            candidate -> targetPath.compareToFirstSegment(candidate.getPath(), caseSensitivity)
        );
    }

    @Override
    public <RESULT> ChildMap<RESULT> invalidate(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, InvalidationHandler<T, RESULT> handler) {
        int childIndex = findChildIndexWithCommonPrefix(targetPath, caseSensitivity);
        if (childIndex >= 0) {
            Entry<T> entry = entries.get(childIndex);
            String childPath = entry.getPath();
            return entry.withNode(targetPath, caseSensitivity, new AbstractInvalidateChildHandler<T, RESULT>(handler) {

                @SuppressWarnings("unchecked")
                @Override
                public AbstractListChildMap<RESULT> getChildMap() {
                    return (AbstractListChildMap<RESULT>) AbstractListChildMap.this;
                }

                @Override
                public ChildMap<RESULT> withReplacedChild(RESULT newChild) {
                    return withReplacedChild(childPath, newChild);
                }

                @Override
                public ChildMap<RESULT> withReplacedChild(String newChildPath, RESULT newChild) {
                    return getChildMap().withReplacedChild(childIndex, newChildPath, newChild);
                }

                @Override
                public ChildMap<RESULT> withRemovedChild() {
                    return getChildMap().withRemovedChild(childIndex);
                }
            });
        } else {
            handler.handleUnrelatedToAnyChild();
            @SuppressWarnings("unchecked") AbstractListChildMap<RESULT> castedThis = (AbstractListChildMap<RESULT>) this;
            return castedThis;
        }
    }

    @Override
    public ChildMap<T> store(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, StoreHandler<T> storeHandler) {
        int childIndex = findChildIndexWithCommonPrefix(targetPath, caseSensitivity);
        if (childIndex >= 0) {
            return entries.get(childIndex).handlePath(targetPath, caseSensitivity, new AbstractStorePathRelationshipHandler<T>(caseSensitivity, storeHandler) {
                @Override
                public ChildMap<T> withReplacedChild(T newChild) {
                    return withReplacedChild(entries.get(childIndex).getPath(), newChild);
                }

                @Override
                public ChildMap<T> withReplacedChild(String newChildPath, T newChild) {
                    return AbstractListChildMap.this.withReplacedChild(childIndex, newChildPath, newChild);
                }

                @Override
                public ChildMap<T> withNewChild(String newChildPath, T newChild) {
                    return AbstractListChildMap.this.withNewChild(childIndex, newChildPath, newChild);
                }
            });
        } else {
            T newChild = storeHandler.createChild();
            return withNewChild(-childIndex - 1, targetPath.toString(), newChild);
        }
    }

    protected ChildMap<T> withNewChild(int insertBefore, String path, T newChild) {
        List<Entry<T>> newChildren = new ArrayList<>(entries);
        newChildren.add(insertBefore, new Entry<>(path, newChild));
        return ChildMapFactory.childMapFromSorted(newChildren);
    }

    protected ChildMap<T> withReplacedChild(int childIndex, String newPath, T newChild) {
        Entry<T> oldEntry = entries.get(childIndex);
        if (oldEntry.getPath().equals(newPath) && oldEntry.getValue().equals(newChild)) {
            return this;
        }
        List<Entry<T>> newChildren = new ArrayList<>(entries);
        newChildren.set(childIndex, new Entry<>(newPath, newChild));
        return ChildMapFactory.childMapFromSorted(newChildren);
    }

    protected ChildMap<T> withRemovedChild(int childIndex) {
        List<Entry<T>> newChildren = new ArrayList<>(entries);
        newChildren.remove(childIndex);
        return ChildMapFactory.childMapFromSorted(newChildren);
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

        return entries.equals(that.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return entries.toString();
    }
}
