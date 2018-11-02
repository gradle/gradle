/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.provider;

import static org.gradle.api.internal.provider.Providers.NULL_VALUE;

/**
 * A basic {@link org.gradle.api.provider.Provider} implementation. Subclasses need to provide a {@link #getOrNull()} implementation.
 */
public abstract class AbstractReadOnlyProvider<T> extends AbstractMinimalProvider<T> {
    @Override
    public T get() {
        T evaluatedValue = getOrNull();
        if (evaluatedValue == null) {
            throw new IllegalStateException(NULL_VALUE);
        }
        return evaluatedValue;
    }
}
