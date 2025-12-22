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

package org.gradle.internal.execution.impl;

import org.gradle.cache.Cache;
import org.gradle.internal.execution.DeferredResult;
import org.gradle.internal.execution.Identity;
import org.gradle.internal.execution.IdentityCache;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public class DefaultIdentityCache<T> implements IdentityCache<T> {
    private final Cache<String, DeferredResult<T>> cache;

    private DefaultIdentityCache(Cache<String, DeferredResult<T>> cache) {
        this.cache = cache;
    }

    @Override
    public DeferredResult<T> get(Identity key, Supplier<DeferredResult<T>> factory) {
        return cache.get(key.getUniqueId(), factory);
    }

    @Override
    public @Nullable DeferredResult<T> getIfPresent(Identity key) {
        return cache.getIfPresent(key.getUniqueId());
    }

    public static <T> IdentityCache<T> of(Cache<String, DeferredResult<T>> cache) {
        return new DefaultIdentityCache<>(cache);
    }
}
