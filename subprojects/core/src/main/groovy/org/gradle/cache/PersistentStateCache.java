/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.cache;

/**
 * A persistent store containing an object of type T.
 *
 * An exclusive lock is held on this cache by this process, to prevent it being modified by another process.
 */
public interface PersistentStateCache<T> {
    T get();

    void set(T newValue);

    void update(UpdateAction<T> updateAction);

    static interface UpdateAction<T> {
        /**
         * should return the new value
         *
         * @param oldValue
         * @return new value
         */
        T update(T oldValue);
    }
}
