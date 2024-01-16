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

/**
 * A key to a typed piece of shared data.
 *
 * @param <T> type of the value behind the key
 * @since 8.7
 */
@Incubating
public interface SharedDataKey<T> {

    /**
     * Creates a key in the root namespace.
     *
     * All keys from the root namespace with the same value type are considered equal.
     *
     * @param type the type of value behind the key
     * @param <T> the type of value behind the key
     * @since 8.7
     */
    static <T> SharedDataKey<T> of(Class<T> type) {
        return new NamespacelessDataKey(type);
    }

    /**
     * Creates a key in the given {@code namespace}.
     *
     * All keys from the same namespace with the same value type are considered equal.
     *
     * @param type the type of value behind the key
     * @param namespace the namespace under which the value will be available
     * @param <T> the type of value behind the key
     * @since 8.7
     */
    static <T> SharedDataKey<T> of(Class<T> type, String namespace) {
        return new NamespacedDataKey(type, namespace);
    }
}
