/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.data;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Provider;

/**
 * Allows build logic to share data via a hierarchically scoped
 * {@link SharedDataKey key}-{@link Provider value} store.
 *
 * @since 8.7
 */
@Incubating
public interface SharedDataScope {

    /**
     * Makes data available in the current scope.
     *
     * @see SharedDataKey#of(Class)
     * @see #set(SharedDataKey, Provider)
     * @since 8.7
     */
    default <T> void register(Class<T> dataType, Provider<? extends T> dataProvider) {
        set(SharedDataKey.of(dataType), dataProvider);
    }

    /**
     * Queries data available in the current scope.
     *
     * @see SharedDataKey#of(Class)
     * @see #get(SharedDataKey)
     * @since 8.7
     */
    default <T> Provider<T> obtain(Class<T> dataType) {
        return get(SharedDataKey.of(dataType));
    }

    /**
     * Makes data available in the current scope.
     *
     * @since 8.7
     */
    <T> void set(SharedDataKey<T> key, Provider<? extends T> dataProvider);

    /**
     * Queries data available in the current scope.
     *
     * @since 8.7
     */
    <T> Provider<T> get(SharedDataKey<T> dataType);
}
