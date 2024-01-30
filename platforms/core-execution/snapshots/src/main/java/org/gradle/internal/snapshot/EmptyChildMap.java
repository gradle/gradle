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

import java.util.stream.Stream;

public class EmptyChildMap<T> implements ChildMap<T> {
    private static final EmptyChildMap<Object> INSTANCE = new EmptyChildMap<>();

    @SuppressWarnings("unchecked")
    public static <T> EmptyChildMap<T> getInstance() {
        return (EmptyChildMap<T>) INSTANCE;
    }

    private EmptyChildMap() {
    }

    @Override
    public <R> R withNode(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, NodeHandler<T, R> handler) {
        return handler.handleUnrelatedToAnyChild();
    }

    @Override
    public <RESULT> ChildMap<RESULT> invalidate(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, InvalidationHandler<T, RESULT> handler) {
        handler.handleUnrelatedToAnyChild();
        return getInstance();
    }

    @Override
    public ChildMap<T> store(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, StoreHandler<T> storeHandler) {
        return new SingletonChildMap<>(targetPath.getAsString(), storeHandler.createChild());
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public Stream<Entry<T>> stream() {
        return Stream.empty();
    }

    @Override
    public String toString() {
        return "";
    }
}
