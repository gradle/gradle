/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.internal;

import org.jspecify.annotations.Nullable;

public final class Factories {

    private Factories() {
        /* no-op */
    }

    public static <T> Factory<@Nullable T> toFactory(final Runnable runnable) {
        return new Factory<@Nullable T>() {
            @Override
            public @Nullable T create() {
                runnable.run();
                return null;
            }
        };
    }

    public static <T> Factory<T> constant(final T item) {
        return new Factory<T>() {
            @Override
            public T create() {
                return item;
            }
        };
    }
}
