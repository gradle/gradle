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

import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.provider.Provider;
import org.gradle.internal.DisplayName;

import javax.annotation.Nullable;

public interface ProviderInternal<T> extends Provider<T>, ValueSupplier, TaskDependencyContainer {
    /**
     * Return the upper bound on the type of all values that this provider may produce, if known.
     *
     * This could probably move to the public API.
     */
    @Nullable
    Class<T> getType();

    @Override
    <S> ProviderInternal<S> map(Transformer<? extends S, ? super T> transformer);

    /**
     * Calculates the value of this provider.
     */
    ValueSupplier.Value<? extends T> calculateValue();

    /**
     * Returns a view of this provider that can be used to supply a value to a {@link org.gradle.api.provider.Property} instance.
     */
    ProviderInternal<T> asSupplier(DisplayName owner, Class<? super T> targetType, ValueSanitizer<? super T> sanitizer);

    /**
     * Returns a copy of this provider with a final value.
     */
    ProviderInternal<T> withFinalValue();
}
