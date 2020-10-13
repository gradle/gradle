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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

public interface ChildMap<T> {

    boolean isEmpty();

    List<T> values();

    void visitChildren(BiConsumer<String, T> visitor);

    <R> R findChild(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, FindChildHandler<T, R> handler);

    interface FindChildHandler<T, RESULT> {
        /**
         * relativePath is a descendant of child.
         */
        RESULT findInChild(String childPath, T child);
        /**
         * child is at relativePath.
         */
        RESULT getFromChild(T child);
        /**
         * relativePath has no common prefix with any child,
         */
        RESULT handleNotFound();
    }

    <R> R getNode(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, GetNodeHandler<T, R> handler);

    interface GetNodeHandler<T, R> {
        R getInChild(VfsRelativePath pathInChild, T child);
        R getForAncestorOf(String childPath, T child);
        R getForChild(T child);
        R notFound();
    }

    <R> ChildMap<R> invalidate(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, InvalidationHandler<T, R> handler);

    interface InvalidationHandler<T, R> {
        Optional<R> invalidateDescendantOfChild(VfsRelativePath pathInChild, T child);
        void ancestorInvalidated(T child);
        void childInvalidated(T child);
        void invalidatedChildNotFound();
    }

    ChildMap<T> store(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, StoreHandler<T> storeHandler);

    interface StoreHandler<T> {
        T storeInChild(VfsRelativePath pathInChild, T child);
        T storeAsAncestor(VfsRelativePath pathToChild, T child);
        T mergeWithExisting(T child);
        T createChild();
        T createNodeFromChildren(ChildMap<T> children);
        Comparator<String> getPathComparator();
    }
}
