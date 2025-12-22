/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.execution;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public interface IdentityCache<T> {

    /**
     * Locates the given entry, using the supplied factory when the entry is not present or has been discarded, to recreate the entry in the cache.
     *
     * <p>Implementations may prevent more than one thread calculating the same key at the same time or not.
     */
    DeferredResult<T> get(Identity key, Supplier<DeferredResult<T>> factory);

    /**
     * Locates the given entry, if present. Returns {@code null} when missing.
     */
    @Nullable
    DeferredResult<T> getIfPresent(Identity key);
}
