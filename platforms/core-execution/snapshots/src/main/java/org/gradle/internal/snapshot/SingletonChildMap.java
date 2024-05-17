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

import static org.gradle.internal.snapshot.ChildMapFactory.childMap;

public class SingletonChildMap<T> implements ChildMap<T> {
    private final Entry<T> entry;

    public SingletonChildMap(String path, T child) {
        this(new Entry<>(path, child));
    }

    public SingletonChildMap(Entry<T> entry) {
        this.entry = entry;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public Stream<Entry<T>> stream() {
        return Stream.of(entry);
    }

    @Override
    public <R> R withNode(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, NodeHandler<T, R> handler) {
        return entry.withNode(targetPath, caseSensitivity, handler);
    }

    @Override
    public <RESULT> ChildMap<RESULT> invalidate(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, InvalidationHandler<T, RESULT> handler) {
        return entry.withNode(targetPath, caseSensitivity, new AbstractInvalidateChildHandler<T, RESULT>(handler) {
            @SuppressWarnings("unchecked")
            @Override
            public SingletonChildMap<RESULT> getChildMap() {
                return (SingletonChildMap<RESULT>) SingletonChildMap.this;
            }

            @Override
            public ChildMap<RESULT> withReplacedChild(RESULT newChild) {
                return withReplacedChild(entry.getPath(), newChild);
            }

            @Override
            public ChildMap<RESULT> withReplacedChild(String newChildPath, RESULT newChild) {
                return getChildMap().withReplacedChild(newChildPath, newChild);
            }

            @Override
            public ChildMap<RESULT> withRemovedChild() {
                return EmptyChildMap.getInstance();
            }
        });
    }

    @Override
    public ChildMap<T> store(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, StoreHandler<T> storeHandler) {
        return entry.handlePath(targetPath, caseSensitivity, new AbstractStorePathRelationshipHandler<T>(caseSensitivity, storeHandler) {
            @Override
            public ChildMap<T> withReplacedChild(T newChild) {
                return withReplacedChild(entry.getPath(), newChild);
            }

            @Override
            public ChildMap<T> withReplacedChild(String newChildPath, T newChild) {
                return SingletonChildMap.this.withReplacedChild(newChildPath, newChild);
            }

            @Override
            public ChildMap<T> withNewChild(String newChildPath, T newChild) {
                return SingletonChildMap.this.withNewChild(caseSensitivity, newChildPath, newChild);
            }

        });
    }

    private ChildMap<T> withNewChild(CaseSensitivity caseSensitivity, String newChildPath, T newChild) {
        Entry<T> newEntry = new Entry<>(newChildPath, newChild);
        return childMap(caseSensitivity, entry, newEntry);
    }

    private <RESULT> ChildMap<RESULT> withReplacedChild(String newPath, RESULT newChild) {
        if (entry.getPath().equals(newPath) && entry.getValue().equals(newChild)) {
            return castThis();
        }
        return new SingletonChildMap<>(newPath, newChild);
    }

    @SuppressWarnings("unchecked")
    private <RESULT> SingletonChildMap<RESULT> castThis() {
        return (SingletonChildMap<RESULT>) this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SingletonChildMap<?> that = (SingletonChildMap<?>) o;

        return entry.equals(that.entry);
    }

    @Override
    public int hashCode() {
        return entry.hashCode();
    }

    @Override
    public String toString() {
        return entry.toString();
    }
}
