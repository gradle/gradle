/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.lazy;

import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * @see Lazy#flatMap(Function)
 */
class FlatMappedLazy<T extends @Nullable Object, V extends @Nullable Object> extends MappedLazy<T, V> {
    FlatMappedLazy(Lazy<T> source, Function<? super T, Lazy<V>> mapper) {
        super(source, mapper.andThen(Lazy::get));
    }

    // Override this because the computation of the actual value from the mapper is expensive.
    @Override
    public boolean isInitialized() {
        return hasMappedValue();
    }
}
