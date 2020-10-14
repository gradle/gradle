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

import java.util.List;
import java.util.Optional;

public abstract class AbstractChildMap<T> implements ChildMap<T> {

    /**
     * If a node has fewer children, we use a linear search for the child.
     * We use this limit since {@link VfsRelativePath#compareToFirstSegment(String, CaseSensitivity)}
     * is about twice as slow as {@link VfsRelativePath#hasPrefix(String, CaseSensitivity)},
     * so comparing the searched path to all of the children is actually faster than doing a binary search.
     */
    private static final int MINIMUM_CHILD_COUNT_FOR_BINARY_SEARCH = 10;

    public static <T> AbstractChildMap<T> childMap(List<Entry<T>> entries) {
        int size = entries.size();
        switch (size) {
            case 0:
                return EmptyChildMap.getInstance();
            case 1:
                return new SingletonChildMap<>(entries.get(0));
            default:
                return (size < MINIMUM_CHILD_COUNT_FOR_BINARY_SEARCH)
                    ? new MediumChildMap<>(entries)
                    : new LargeChildMap<>(entries);
        }
    }

    protected abstract <R> R handlePath(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, PathRelationshipHandler<R> handler);

    protected abstract T get(int index);

    @Override
    public <R> R getNode(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, GetNodeHandler<T, R> handler) {
        return handlePath(targetPath, caseSensitivity, new PathRelationshipHandler<R>() {
            @Override
            public R handleAsDescendantOfChild(VfsRelativePath targetPath, String childPath, int childIndex) {
                return handler.handleAsDescendantOfChild(targetPath.fromChild(childPath), get(childIndex));
            }

            @Override
            public R handleAsAncestorOfChild(VfsRelativePath targetPath, String childPath, int childIndex) {
                return handler.handleAsAncestorOfChild(childPath, get(childIndex));
            }

            @Override
            public R handleExactMatchWithChild(VfsRelativePath targetPath, String childPath, int childIndex) {
                return handler.handleExactMatchWithChild(get(childIndex));
            }

            @Override
            public R handleSiblingOfChild(VfsRelativePath targetPath, String childPath, int childIndex, int commonPrefixLength) {
                return handler.handleUnrelatedToAnyChild();
            }

            @Override
            public R handleUnrelatedToAnyChild(VfsRelativePath targetPath, int indexOfNextBiggerChild) {
                return handler.handleUnrelatedToAnyChild();
            }
        });
    }

    @Override
    public <R> ChildMap<R> invalidate(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, InvalidationHandler<T, R> handler) {
        return handlePath(targetPath, caseSensitivity, new PathRelationshipHandler<ChildMap<R>>() {
            @Override
            public ChildMap<R> handleAsDescendantOfChild(VfsRelativePath targetPath, String childPath, int childIndex) {
                T oldChild = get(childIndex);
                Optional<R> invalidatedChild = handler.handleAsDescendantOfChild(targetPath.fromChild(childPath), oldChild);
                return invalidatedChild
                    .map(newChild -> cast(AbstractChildMap.this).withReplacedChild(childIndex, childPath, newChild))
                    .orElseGet(() -> cast(withRemovedChild(childIndex)));
            }

            @Override
            public ChildMap<R> handleAsAncestorOfChild(VfsRelativePath targetPath, String childPath, int childIndex) {
                handler.handleAsAncestorOfChild(childPath, get(childIndex));
                return cast(withRemovedChild(childIndex));
            }

            @Override
            public ChildMap<R> handleExactMatchWithChild(VfsRelativePath targetPath, String childPath, int childIndex) {
                handler.handleExactMatchWithChild(get(childIndex));
                return cast(withRemovedChild(childIndex));
            }

            @Override
            public ChildMap<R> handleSiblingOfChild(VfsRelativePath targetPath, String childPath, int childIndex, int commonPrefixLength) {
                handler.handleUnrelatedToAnyChild();
                return cast(AbstractChildMap.this);
            }

            @Override
            public ChildMap<R> handleUnrelatedToAnyChild(VfsRelativePath targetPath, int indexOfNextBiggerChild) {
                handler.handleUnrelatedToAnyChild();
                return cast(AbstractChildMap.this);
            }

            @SuppressWarnings("unchecked")
            private AbstractChildMap<R> cast(AbstractChildMap<T> currentMap) {
                return (AbstractChildMap<R>) currentMap;
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
                ChildMap<T> newChildren = childMap(storeHandler.getPathComparator().compare(newChild.getPath(), sibling.getPath()) < 0
                    ? ImmutableList.of(newChild, sibling)
                    : ImmutableList.of(sibling, newChild)
                );
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

    protected abstract AbstractChildMap<T> withNewChild(int insertBefore, String path, T newChild);

    protected abstract AbstractChildMap<T> withReplacedChild(int childIndex, String childPath, T newChild);

    protected abstract AbstractChildMap<T> withRemovedChild(int childIndex);

    public interface PathRelationshipHandler<RESULT> {
        RESULT handleAsDescendantOfChild(VfsRelativePath targetPath, String childPath, int childIndex);
        RESULT handleAsAncestorOfChild(VfsRelativePath targetPath, String childPath, int childIndex);
        RESULT handleExactMatchWithChild(VfsRelativePath targetPath, String childPath, int childIndex);
        RESULT handleSiblingOfChild(VfsRelativePath targetPath, String childPath, int childIndex, int commonPrefixLength);
        RESULT handleUnrelatedToAnyChild(VfsRelativePath targetPath, int indexOfNextBiggerChild);
    }

    public static class Entry<T> {
        private final String path;
        private final T value;

        public Entry(String path, T value) {
            this.path = path;
            this.value = value;
        }

        public <RESULT> RESULT findPath(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, FindChildHandler<T, RESULT> handler) {
            if (!targetPath.hasPrefix(path, caseSensitivity)) {
                return handler.handleUnrelatedToAnyChild();
            }
            return targetPath.length() == path.length()
                ? handler.handleExactMatchWithChild(value)
                : handler.handleAsDescendantOfChild(targetPath.fromChild(path), value);
        }

        public <RESULT> RESULT handlePath(VfsRelativePath targetPath, int currentChildIndex, CaseSensitivity caseSensitivity, PathRelationshipHandler<RESULT> handler) {
            int pathToParentLength = path.length();
            int targetPathLength = targetPath.length();
            int maxPos = Math.min(pathToParentLength, targetPathLength);
            int commonPrefixLength = targetPath.lengthOfCommonPrefix(path, caseSensitivity);
            if (commonPrefixLength == maxPos) {
                if (pathToParentLength > targetPathLength) {
                    return handler.handleAsAncestorOfChild(targetPath, path, currentChildIndex);
                }
                if (pathToParentLength == targetPathLength) {
                    return handler.handleExactMatchWithChild(targetPath, path, currentChildIndex);
                }
                return handler.handleAsDescendantOfChild(targetPath, path, currentChildIndex);
            }
            if (commonPrefixLength == 0) {
                int compared = targetPath.compareToFirstSegment(path, caseSensitivity);
                return handler.handleUnrelatedToAnyChild(targetPath, compared < 0 ? currentChildIndex : currentChildIndex + 1);
            }
            return handler.handleSiblingOfChild(targetPath, path, currentChildIndex, commonPrefixLength);
        }

        public String getPath() {
            return path;
        }

        public T getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Entry<?> entry = (Entry<?>) o;

            if (!path.equals(entry.path)) {
                return false;
            }
            return value.equals(entry.value);
        }

        @Override
        public int hashCode() {
            int result = path.hashCode();
            result = 31 * result + value.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Entry{" + path + " : " + value + '}';
        }
    }
}
