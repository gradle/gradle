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

    public static <T> ChildMap<T> of(List<Entry<T>> entries) {
        switch (entries.size()) {
            case 0:
                return EmptyChildMap.getInstance();
            case 1:
                return new SingletonChildMap<>(entries.get(0));
            default:
                return new DefaultChildMap<>(entries);
        }
    }

    protected abstract <R> R handlePath(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, PathRelationshipHandler<R> handler);

    protected abstract T get(int index);

    @Override
    public <R> R getNode(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, GetNodeHandler<T, R> handler) {
        return handlePath(relativePath, caseSensitivity, new PathRelationshipHandler<R>() {
            @Override
            public R handleDescendant(String childPath, int childIndex) {
                return handler.getInChild(relativePath.fromChild(childPath), get(childIndex));
            }

            @Override
            public R handleAncestor(String childPath, int childIndex) {
                return handler.getForAncestorOf(childPath, get(childIndex));
            }

            @Override
            public R handleSame(int childIndex) {
                return handler.getForChild(get(childIndex));
            }

            @Override
            public R handleCommonPrefix(int commonPrefixLength, String childPath, int childIndex) {
                return handler.notFound();
            }

            @Override
            public R handleDifferent(int indexOfNextBiggerChild) {
                return handler.notFound();
            }
        });
    }

    @Override
    public <R> ChildMap<R> invalidate(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, InvalidationHandler<T, R> handler) {
        return handlePath(relativePath, caseSensitivity, new PathRelationshipHandler<ChildMap<R>>() {
            @Override
            public ChildMap<R> handleDescendant(String childPath, int childIndex) {
                T oldChild = get(childIndex);
                Optional<R> invalidatedChild = handler.invalidateDescendantOfChild(relativePath.fromChild(childPath), oldChild);
                return invalidatedChild
                    .map(newChild -> cast(AbstractChildMap.this).withReplacedChild(childIndex, childPath, newChild))
                    .orElseGet(() -> cast(withRemovedChild(childIndex)));
            }

            @Override
            public ChildMap<R> handleAncestor(String childPath, int childIndex) {
                handler.ancestorInvalidated(get(childIndex));
                return cast(withRemovedChild(childIndex));
            }

            @Override
            public ChildMap<R> handleSame(int childIndex) {
                handler.childInvalidated(get(childIndex));
                return cast(withRemovedChild(childIndex));
            }

            @Override
            public ChildMap<R> handleCommonPrefix(int commonPrefixLength, String childPath, int childIndex) {
                handler.invalidatedChildNotFound();
                return cast(AbstractChildMap.this);
            }

            @Override
            public ChildMap<R> handleDifferent(int indexOfNextBiggerChild) {
                handler.invalidatedChildNotFound();
                return cast(AbstractChildMap.this);
            }

            @SuppressWarnings("unchecked")
            private AbstractChildMap<R> cast(AbstractChildMap<T> currentMap) {
                return (AbstractChildMap<R>) currentMap;
            }
        });
    }

    @Override
    public ChildMap<T> store(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, StoreHandler<T> storeHandler) {
        return handlePath(relativePath, caseSensitivity, new PathRelationshipHandler<ChildMap<T>>() {
            @Override
            public ChildMap<T> handleDescendant(String childPath, int childIndex) {
                T oldChild = get(childIndex);
                T newChild = storeHandler.storeInChild(relativePath.fromChild(childPath), oldChild);
                return withReplacedChild(childIndex, childPath, newChild);
            }

            @Override
            public ChildMap<T> handleAncestor(String childPath, int childIndex) {
                T newChild = storeHandler.storeAsAncestor(VfsRelativePath.of(childPath).suffixStartingFrom(relativePath.length() + 1), get(childIndex));
                return withReplacedChild(childIndex, relativePath.getAsString(), newChild);
            }

            @Override
            public ChildMap<T> handleSame(int childIndex) {
                T newChild = storeHandler.mergeWithExisting(get(childIndex));
                return withReplacedChild(childIndex, relativePath.getAsString(), newChild);
            }

            @Override
            public ChildMap<T> handleCommonPrefix(int commonPrefixLength, String childPath, int childIndex) {
                T oldChild = get(childIndex);
                String commonPrefix = childPath.substring(0, commonPrefixLength);
                String newChildPath = childPath.substring(commonPrefixLength + 1);
                Entry<T> newChild = new Entry<>(newChildPath, oldChild);
                String siblingPath = relativePath.suffixStartingFrom(commonPrefixLength + 1).getAsString();
                Entry<T> sibling = new Entry<>(siblingPath, storeHandler.createChild());
                ChildMap<T> newChildren = of(storeHandler.getPathComparator().compare(newChild.getPath(), sibling.getPath()) < 0
                    ? ImmutableList.of(newChild, sibling)
                    : ImmutableList.of(sibling, newChild)
                );
                return withReplacedChild(childIndex, commonPrefix, storeHandler.createNodeFromChildren(newChildren));
            }

            @Override
            public ChildMap<T> handleDifferent(int indexOfNextBiggerChild) {
                String path = relativePath.getAsString();
                T newNode = storeHandler.createChild();
                return withNewChild(indexOfNextBiggerChild, path, newNode);
            }
        });
    }

    protected abstract int indexOf(String path, CaseSensitivity caseSensitivity);

    protected abstract AbstractChildMap<T> withNewChild(int insertBefore, String path, T newChild);

    protected abstract AbstractChildMap<T> withReplacedChild(int childIndex, String childPath, T newChild);

    protected abstract AbstractChildMap<T> withRemovedChild(int childIndex);

    public interface PathRelationshipHandler<RESULT> {
        /**
         * relativePath is a descendant of child.
         */
        RESULT handleDescendant(String childPath, int childIndex);
        /**
         * relativePath is an ancestor of child.
         */
        RESULT handleAncestor(String childPath, int childIndex);
        /**
         * child is at relativePath.
         */
        RESULT handleSame(int childIndex);
        /**
         * relativePath has a real common prefix with child,
         */
        RESULT handleCommonPrefix(int commonPrefixLength, String childPath, int childIndex);
        /**
         * relativePath has no common prefix with any child,
         */
        RESULT handleDifferent(int indexOfNextBiggerChild);
    }

    public static class Entry<T> {
        private final String path;
        private final T value;

        public Entry(String path, T value) {
            this.path = path;
            this.value = value;
        }

        public <R> R findPath(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, FindChildHandler<T, R> handler) {
            if (!relativePath.hasPrefix(path, caseSensitivity)) {
                return handler.handleNotFound();
            }
            return relativePath.length() == path.length()
                ? handler.getFromChild(value)
                : handler.findInChild(path, value);
        }

        public <R> R handlePath(VfsRelativePath relativePath, int currentChildIndex, CaseSensitivity caseSensitivity, PathRelationshipHandler<R> handler) {
            int pathToParentLength = path.length();
            int relativePathLength = relativePath.length();
            int maxPos = Math.min(pathToParentLength, relativePathLength);
            int commonPrefixLength = relativePath.lengthOfCommonPrefix(path, caseSensitivity);
            if (commonPrefixLength == maxPos) {
                if (pathToParentLength > relativePathLength) {
                    return handler.handleAncestor(path, currentChildIndex);
                }
                if (pathToParentLength == relativePathLength) {
                    return handler.handleSame(currentChildIndex);
                }
                return handler.handleDescendant(path, currentChildIndex);
            }
            if (commonPrefixLength == 0) {
                int compared = relativePath.compareToFirstSegment(path, caseSensitivity);
                return handler.handleDifferent(compared < 0 ? currentChildIndex : currentChildIndex + 1);
            }
            return handler.handleCommonPrefix(commonPrefixLength, path, currentChildIndex);
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
