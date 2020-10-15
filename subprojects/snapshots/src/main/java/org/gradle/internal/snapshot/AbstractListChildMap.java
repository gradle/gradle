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
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public abstract class AbstractListChildMap<T> implements ChildMap<T> {
    protected final List<Entry<T>> entries;

    protected AbstractListChildMap(List<Entry<T>> entries) {
        this.entries = entries;
    }

    protected int findChildIndexWithCommonPrefix(VfsRelativePath targetPath, CaseSensitivity caseSensitivity) {
        return SearchUtil.binarySearch(
            entries,
            candidate -> targetPath.compareToFirstSegment(candidate.getPath(), caseSensitivity)
        );
    }

    protected <RESULT> RESULT handlePath(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, PathRelationshipHandler<RESULT> handler) {
        int childIndex = findChildIndexWithCommonPrefix(targetPath, caseSensitivity);
        if (childIndex >= 0) {
            return entries.get(childIndex).handlePath(targetPath, childIndex, caseSensitivity, handler);
        }
        return handler.handleUnrelatedToAnyChild(targetPath, -childIndex - 1);
    }

    protected T get(int index) {
        return entries.get(index).getValue();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public List<T> values() {
        return entries.stream()
            .map(Entry::getValue)
            .collect(Collectors.toList());
    }

    @Override
    public List<Entry<T>> entries() {
        return entries;
    }

    protected ChildMap<T> withNewChild(int insertBefore, String path, T newChild) {
        List<Entry<T>> newChildren = new ArrayList<>(entries);
        newChildren.add(insertBefore, new Entry<>(path, newChild));
        return ChildMapFactory.childMap(newChildren);
    }

    protected ChildMap<T> withReplacedChild(int childIndex, String newPath, T newChild) {
        Entry<T> oldEntry = entries.get(childIndex);
        if (oldEntry.getPath().equals(newPath) && oldEntry.getValue().equals(newChild)) {
            return this;
        }
        List<Entry<T>> newChildren = new ArrayList<>(entries);
        newChildren.set(childIndex, new Entry<>(newPath, newChild));
        return ChildMapFactory.childMap(newChildren);
    }

    protected ChildMap<T> withRemovedChild(int childIndex) {
        List<Entry<T>> newChildren = new ArrayList<>(entries);
        newChildren.remove(childIndex);
        return ChildMapFactory.childMap(newChildren);
    }

    @Override
    public void visitChildren(BiConsumer<String, T> visitor) {
        for (Entry<T> child : entries) {
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

        return entries.equals(that.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public <RESULT> ChildMap<RESULT> invalidate(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, InvalidationHandler<T, RESULT> handler) {
        return handlePath(targetPath, caseSensitivity, new PathRelationshipHandler<ChildMap<RESULT>>() {
            @Override
            public ChildMap<RESULT> handleAsDescendantOfChild(VfsRelativePath targetPath, String childPath, int childIndex) {
                T oldChild = get(childIndex);
                Optional<RESULT> invalidatedChild = handler.handleAsDescendantOfChild(targetPath.fromChild(childPath), oldChild);
                return invalidatedChild
                    .map(newChild -> ((AbstractListChildMap<RESULT>) cast(AbstractListChildMap.this)).withReplacedChild(childIndex, childPath, newChild))
                    .orElseGet(() -> cast(withRemovedChild(childIndex)));
            }

            @Override
            public ChildMap<RESULT> handleAsAncestorOfChild(VfsRelativePath targetPath, String childPath, int childIndex) {
                handler.handleAsAncestorOfChild(childPath, get(childIndex));
                return cast(withRemovedChild(childIndex));
            }

            @Override
            public ChildMap<RESULT> handleExactMatchWithChild(VfsRelativePath targetPath, String childPath, int childIndex) {
                handler.handleExactMatchWithChild(get(childIndex));
                return cast(withRemovedChild(childIndex));
            }

            @Override
            public ChildMap<RESULT> handleSiblingOfChild(VfsRelativePath targetPath, String childPath, int childIndex, int commonPrefixLength) {
                handler.handleUnrelatedToAnyChild();
                return cast(AbstractListChildMap.this);
            }

            @Override
            public ChildMap<RESULT> handleUnrelatedToAnyChild(VfsRelativePath targetPath, int indexOfNextBiggerChild) {
                handler.handleUnrelatedToAnyChild();
                return cast(AbstractListChildMap.this);
            }

            @SuppressWarnings("unchecked")
            private <S extends ChildMap<RESULT>> S cast(ChildMap<T> currentMap) {
                return (S) currentMap;
            }
        });
    }

    @Override
    public ChildMap<T> store(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, StoreHandler<T> storeHandler) {
        return handlePath(targetPath, caseSensitivity, new PathRelationshipHandler<ChildMap<T>>() {
            @Override
            public ChildMap<T> handleAsDescendantOfChild(VfsRelativePath targetPath, String childPath, int childIndex) {
                T oldChild = get(childIndex);
                T newChild = storeHandler.handleAsDescendantOfChild(targetPath.fromChild(childPath), oldChild);
                return withReplacedChild(childIndex, childPath, newChild);
            }

            @Override
            public ChildMap<T> handleAsAncestorOfChild(VfsRelativePath targetPath, String childPath, int childIndex) {
                T newChild = storeHandler.handleAsAncestorOfChild(childPath, get(childIndex));
                return withReplacedChild(childIndex, targetPath.getAsString(), newChild);
            }

            @Override
            public ChildMap<T> handleExactMatchWithChild(VfsRelativePath targetPath, String childPath, int childIndex) {
                T newChild = storeHandler.mergeWithExisting(get(childIndex));
                return withReplacedChild(childIndex, childPath, newChild);
            }

            @Override
            public ChildMap<T> handleSiblingOfChild(VfsRelativePath targetPath, String childPath, int childIndex, int commonPrefixLength) {
                T oldChild = get(childIndex);
                String commonPrefix = childPath.substring(0, commonPrefixLength);
                String newChildPath = childPath.substring(commonPrefixLength + 1);
                Entry<T> newChild = new Entry<>(newChildPath, oldChild);
                String siblingPath = targetPath.suffixStartingFrom(commonPrefixLength + 1).getAsString();
                Entry<T> sibling = new Entry<>(siblingPath, storeHandler.createChild());
                ChildMap<T> newChildren = ChildMapFactory.childMap(caseSensitivity, newChild, sibling);
                return withReplacedChild(childIndex, commonPrefix, storeHandler.createNodeFromChildren(newChildren));
            }

            @Override
            public ChildMap<T> handleUnrelatedToAnyChild(VfsRelativePath targetPath, int indexOfNextBiggerChild) {
                String path = targetPath.getAsString();
                T newNode = storeHandler.createChild();
                return withNewChild(indexOfNextBiggerChild, path, newNode);
            }
        });
    }

    public interface PathRelationshipHandler<RESULT> {
        RESULT handleAsDescendantOfChild(VfsRelativePath targetPath, String childPath, int childIndex);
        RESULT handleAsAncestorOfChild(VfsRelativePath targetPath, String childPath, int childIndex);
        RESULT handleExactMatchWithChild(VfsRelativePath targetPath, String childPath, int childIndex);
        RESULT handleSiblingOfChild(VfsRelativePath targetPath, String childPath, int childIndex, int commonPrefixLength);
        RESULT handleUnrelatedToAnyChild(VfsRelativePath targetPath, int indexOfNextBiggerChild);
    }
}
