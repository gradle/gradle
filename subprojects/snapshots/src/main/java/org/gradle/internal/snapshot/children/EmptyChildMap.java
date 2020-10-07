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

import java.util.Optional;

public class EmptyChildMap<T> implements ChildMap<T> {
    public EmptyChildMap(CaseSensitivity caseSensitivity) {
        this.caseSensitivity = caseSensitivity;
    }

    private final CaseSensitivity caseSensitivity;

    @Override
    public Optional<T> get(VfsRelativePath relativePath) {
        return Optional.empty();
    }

    @Override
    public <R> R handlePath(VfsRelativePath relativePath, PathRelationshipHandler<R> handler) {
        return handler.handleDifferent(0);
    }

    @Override
    public T get(int index) {
        throw indexOutOfBoundsException(index);
    }

    @Override
    public ChildMap<T> withNewChild(int insertBefore, String path, T newChild) {
        if (insertBefore != 0) {
            throw indexOutOfBoundsException(insertBefore);
        }
        return new SingletonChildMap<>(path, newChild, caseSensitivity);
    }

    @Override
    public ChildMap<T> withReplacedChild(int childIndex, T newChild) {
        throw indexOutOfBoundsException(childIndex);
    }

    @Override
    public ChildMap<T> withRemovedChild(int childIndex) {
        throw indexOutOfBoundsException(childIndex);
    }

    private static IndexOutOfBoundsException indexOutOfBoundsException(int childIndex) {
        return new IndexOutOfBoundsException("Index out of range: " + childIndex);
    }
}
