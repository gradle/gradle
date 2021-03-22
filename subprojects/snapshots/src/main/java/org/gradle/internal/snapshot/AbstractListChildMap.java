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
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public abstract class AbstractListChildMap<T> implements ChildMap<T> {
    protected final Entry<T>[] entries;

    protected AbstractListChildMap(Entry<T>[] entries) {
        this.entries = entries;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public List<T> values() {
        List<T> values = new ArrayList<>(entries.length);
        for (Entry<T> entry : entries) {
            values.add(entry.getValue());
        }
        return values;
    }

    @Override
    public Stream<Entry<T>> entries() {
        return Arrays.stream(entries);
    }

    @Override
    public void visitChildren(BiConsumer<String, ? super T> visitor) {
        for (Entry<T> child : entries) {
            visitor.accept(child.getPath(), child.getValue());
        }
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
            Entry<T> entry = entries[childIndex];
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
            return entries[childIndex].handlePath(targetPath, caseSensitivity, new AbstractStorePathRelationshipHandler<T>(caseSensitivity, storeHandler) {
                @Override
                public ChildMap<T> withReplacedChild(T newChild) {
                    return withReplacedChild(entries[childIndex].getPath(), newChild);
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
        Entry<T>[] newChildren = createEntryArray(entries.length + 1);
        if (insertBefore > 0) {
            System.arraycopy(entries, 0, newChildren, 0, insertBefore);
        }
        newChildren[insertBefore] = new Entry<>(path, newChild);
        if (insertBefore < entries.length) {
            System.arraycopy(entries, insertBefore, newChildren, insertBefore + 1, entries.length - insertBefore);
        }
        return ChildMapFactory.childMapFromSorted(newChildren);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Entry<T>[] createEntryArray(int length) {
        return new Entry[length];
    }

    protected ChildMap<T> withReplacedChild(int childIndex, String newPath, T newChild) {
        Entry<T> oldEntry = entries[childIndex];
        if (oldEntry.getPath().equals(newPath) && oldEntry.getValue().equals(newChild)) {
            return this;
        }
        Entry<T>[] newChildren = createEntryArray(entries.length);
        if (childIndex > 0) {
            System.arraycopy(entries, 0, newChildren, 0, childIndex);
        }
        newChildren[childIndex] = new Entry<>(newPath, newChild);
        if (childIndex + 1 < entries.length) {
            System.arraycopy(entries, childIndex + 1, newChildren, childIndex + 1, entries.length - childIndex - 1);
        }
        return ChildMapFactory.childMapFromSorted(newChildren);
    }

    protected ChildMap<T> withRemovedChild(int childIndex) {
        Entry<T>[] newChildren = createEntryArray(entries.length - 1);
        if (childIndex > 0) {
            System.arraycopy(entries, 0, newChildren, 0, childIndex);
        }
        if (childIndex + 1 < entries.length) {
            System.arraycopy(entries, childIndex + 1, newChildren, childIndex, entries.length - childIndex - 1);
        }
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

        return Arrays.equals(entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(entries);
    }

    @Override
    public String toString() {
        return Arrays.toString(entries);
    }
}
