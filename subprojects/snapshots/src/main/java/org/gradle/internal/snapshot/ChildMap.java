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

    <RESULT> RESULT getNode(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, GetNodeHandler<T, RESULT> handler);

    interface GetNodeHandler<T, RESULT> {
        RESULT handleAsDescendantOfChild(VfsRelativePath pathInChild, T child);
        RESULT handleExactMatchWithChild(T child);
        RESULT handleUnrelatedToAnyChild();
        RESULT handleAsAncestorOfChild(String childPath, T child);
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
        Comparator<String> getPathComparator();
    }
}
