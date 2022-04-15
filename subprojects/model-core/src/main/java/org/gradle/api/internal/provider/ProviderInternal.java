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
import java.util.function.BiFunction;

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
     * Calculates the current value of this provider.
     */
    ValueSupplier.Value<? extends T> calculateValue(ValueConsumer consumer);

    /**
     * Returns a view of this provider that can be used to supply a value to a {@link org.gradle.api.provider.Property} instance.
     */
    ProviderInternal<T> asSupplier(DisplayName owner, Class<? super T> targetType, ValueSanitizer<? super T> sanitizer);

    /**
     * Returns a copy of this provider with a final value. The returned value is used to replace this provider by a property when the property is finalized.
     */
    ProviderInternal<T> withFinalValue(ValueConsumer consumer);

    /**
     * Calculates the state of this provider that is required at execution time. The state is serialized to the configuration cache, and recreated as a {@link Provider} implementation
     * when the cache is read.
     *
     * <p>When the value and value content of this provider is known at the completion of configuration, then returns a fixed value or missing value.
     * For example, a String @Input property of a task might have a value that is calculated at configuration time, but once configured does not change.
     *
     * <p>When the value or value content of this provider is not known until execution time then returns a {@link Provider} representing the calculation to perform at execution time.
     * For example, the value content of an @InputFile property of a task is not known when that input file is the output of another a task.
     * The provider returned by this method may or not be the same instance as this provider. Generally, it is better to simplify any provider chains to replace calculations with fixed values and to remove
     * intermediate steps.
     */
    ExecutionTimeValue<? extends T> calculateExecutionTimeValue();

    default <U, R> Provider<R> zip(Provider<U> right, BiFunction<? super T, ? super U, ? extends R> combiner) {
        return new BiProvider<>(this, right, combiner);
    }
}
