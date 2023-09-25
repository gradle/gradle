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

public abstract class AbstractInvalidateChildHandler<T, RESULT> implements ChildMap.NodeHandler<T, ChildMap<RESULT>> {

    private final ChildMap.InvalidationHandler<T, RESULT> handler;

    public AbstractInvalidateChildHandler(ChildMap.InvalidationHandler<T, RESULT> handler) {
        this.handler = handler;
    }

    public abstract ChildMap<RESULT> getChildMap();

    public abstract ChildMap<RESULT> withReplacedChild(RESULT newChild);

    public abstract ChildMap<RESULT> withReplacedChild(String newChildPath, RESULT newChild);

    public abstract ChildMap<RESULT> withRemovedChild();

    @Override
    public ChildMap<RESULT> handleAsDescendantOfChild(VfsRelativePath pathInChild, T child) {
        Optional<RESULT> invalidatedChild = handler.handleAsDescendantOfChild(pathInChild, child);
        return invalidatedChild
            .map(this::withReplacedChild)
            .orElseGet(this::withRemovedChild);
    }

    @Override
    public ChildMap<RESULT> handleAsAncestorOfChild(String childPath, T child) {
        handler.handleAsAncestorOfChild(childPath, child);
        return withRemovedChild();
    }

    @Override
    public ChildMap<RESULT> handleExactMatchWithChild(T child) {
        handler.handleExactMatchWithChild(child);
        return withRemovedChild();
    }

    @Override
    public ChildMap<RESULT> handleUnrelatedToAnyChild() {
        handler.handleUnrelatedToAnyChild();
        return getChildMap();
    }
}
