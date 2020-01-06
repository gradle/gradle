/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.internal.DisplayName;

import javax.annotation.Nullable;

/**
 * Supplies zero or one value of type {@link T}.
 */
public interface ScalarSupplier<T> extends ValueSupplier {
    boolean isPresent();

    /**
     * Returns the value of this supplier or fails.
     *
     * @param owner A display name that can be used in error messages.
     */
    T get(DisplayName owner) throws IllegalStateException;

    @Nullable
    T getOrNull();

    ProviderInternal<T> asProvider();

    ScalarSupplier<T> withFinalValue();
}
