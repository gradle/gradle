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

package org.gradle.internal.snapshot.children;

import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.VfsRelativePath;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

public interface ChildMap<T> {
    Optional<T> get(VfsRelativePath relativePath);

    <R> R handlePath(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, PathRelationshipHandler<R> handler);

    T get(int index);

    boolean isEmpty();

    List<T> values();

    ChildMap<T> withNewChild(int insertBefore, String path, T newChild);

    ChildMap<T> withReplacedChild(int childIndex, String childPath, T newChild);

    ChildMap<T> withRemovedChild(int childIndex);

    void visitChildren(BiConsumer<String, T> visitor);

    class Entry<T> {
        private final String path;
        private final T value;

        public Entry(String path, T value) {
            this.path = path;
            this.value = value;
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
    }

    interface PathRelationshipHandler<RESULT> {
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
}
