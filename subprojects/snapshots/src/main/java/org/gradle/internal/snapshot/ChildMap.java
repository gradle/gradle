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

import java.util.Optional;
import java.util.stream.Stream;

public interface ChildMap<T> {

    boolean isEmpty();

    Stream<Entry<T>> stream();

    <RESULT> RESULT withNode(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, NodeHandler<T, RESULT> handler);

    interface NodeHandler<T, RESULT> {
        RESULT handleAsDescendantOfChild(VfsRelativePath pathInChild, T child);
        RESULT handleAsAncestorOfChild(String childPath, T child);
        RESULT handleExactMatchWithChild(T child);
        RESULT handleUnrelatedToAnyChild();
    }

    <RESULT> ChildMap<RESULT> invalidate(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, InvalidationHandler<T, RESULT> handler);

    interface InvalidationHandler<T, RESULT> {
        Optional<RESULT> handleAsDescendantOfChild(VfsRelativePath pathInChild, T child);
        void handleAsAncestorOfChild(String childPath, T child);
        void handleExactMatchWithChild(T child);
        void handleUnrelatedToAnyChild();
    }

    ChildMap<T> store(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, StoreHandler<T> storeHandler);

    interface StoreHandler<T> {
        T handleAsDescendantOfChild(VfsRelativePath pathInChild, T child);
        T handleAsAncestorOfChild(String childPath, T child);
        T mergeWithExisting(T child);
        T createChild();
        T createNodeFromChildren(ChildMap<T> children);
    }

    class Entry<T> {
        private final String path;
        private final T value;

        public Entry(String path, T value) {
            this.path = path;
            this.value = value;
        }

        public <RESULT> RESULT withNode(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, NodeHandler<T, RESULT> handler) {
            return handleAncestorDescendantOrExactMatch(targetPath, caseSensitivity, handler)
                .orElseGet(handler::handleUnrelatedToAnyChild);
        }

        public <RESULT> Optional<RESULT> handleAncestorDescendantOrExactMatch(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, NodeHandler<T, RESULT> handler) {
            if (targetPath.hasPrefix(path, caseSensitivity)) {
                if (targetPath.length() == path.length()) {
                    return Optional.of(handler.handleExactMatchWithChild(value));
                } else {
                    return Optional.of(handler.handleAsDescendantOfChild(targetPath.fromChild(path), value));
                }
            } else if (targetPath.length() < path.length() && targetPath.isPrefixOf(path, caseSensitivity)) {
                return Optional.of(handler.handleAsAncestorOfChild(path, value));
            }
            return Optional.empty();
        }

        public <RESULT> RESULT handlePath(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, PathRelationshipHandler<RESULT, T> handler) {
            int pathToParentLength = path.length();
            int targetPathLength = targetPath.length();
            int maxPos = Math.min(pathToParentLength, targetPathLength);
            int commonPrefixLength = targetPath.lengthOfCommonPrefix(path, caseSensitivity);
            if (commonPrefixLength == maxPos) {
                if (pathToParentLength > targetPathLength) {
                    return handler.handleAsAncestorOfChild(targetPath, path, value);
                }
                if (pathToParentLength == targetPathLength) {
                    return handler.handleExactMatchWithChild(targetPath, path, value);
                }
                return handler.handleAsDescendantOfChild(targetPath, path, value);
            }
            if (commonPrefixLength == 0) {
                return handler.handleUnrelatedToAnyChild(targetPath);
            }
            return handler.handleSiblingOfChild(targetPath, path, value, commonPrefixLength);
        }

        public interface PathRelationshipHandler<RESULT, T> {
            RESULT handleAsDescendantOfChild(VfsRelativePath targetPath, String childPath, T child);
            RESULT handleAsAncestorOfChild(VfsRelativePath targetPath, String childPath, T child);
            RESULT handleExactMatchWithChild(VfsRelativePath targetPath, String childPath, T child);
            RESULT handleSiblingOfChild(VfsRelativePath targetPath, String childPath, T child, int commonPrefixLength);
            RESULT handleUnrelatedToAnyChild(VfsRelativePath targetPath);
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
