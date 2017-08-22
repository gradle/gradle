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
 */
public interface PersistentStateCache<T> {
    /**
     * Fetches the value from this cache. A shared or exclusive lock is held while fetching the value, depending on implementation.
     */
    T get();

    /**
     * Sets the value for this cache. An exclusive lock is held while setting the value.
     */
    void set(T newValue);

    /**
     * Replaces the value for this cache.
     *
     * An exclusive lock is held while the update action is executing.
     * The result of the update is returned.
     */
    T update(UpdateAction<T> updateAction);

    interface UpdateAction<T> {
        T update(T oldValue);
    }

    /**
     * Potentially replaces the value for this cache
     *
     * An exclusive lock is held while the update action is executing.
     * The result of the update is returned.
     *
     * If the action returns the existing value, based on referential integrity,
     * it is not written back to the store.
     * That is, it is assumed that values are immutable.
     * To force a write back to the store, ensure that you return a new object.
     */
    T maybeUpdate(UpdateAction<T> updateAction);

}
