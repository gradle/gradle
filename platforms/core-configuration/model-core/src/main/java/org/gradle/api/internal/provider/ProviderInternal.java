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

/**
 * <p>The internal view of a {@link Provider}.</p>
 *
 * <p>A {@code Provider<T>} has several different pieces of state associated with it:</p>
 *
 * <ul>
 *     <li>The "value" of the provider, which is the object returned by {@link #get()} and its variants.</li>
 *     <li>the "content" of the value. The value points to some object, but this object may not be immutable even though the value is fixed.
 *     For example, when the value is a {@code File} object, the state of that file in the file system may change. This mutable state is the "content" of the value.</li>
 * </ul>
 *
 * <p>In other words, a provider does not provide a value, it provides a value whose content is in a particular state. This is discussed in more detail below.</p>
 *
 * <h2>The provider value</h2>
 *
 * <p>The value of a provider may be:</p>
 *
 * <ul>
 *     <li>"fixed", which means that the value is a function only of configuration time inputs.
 *     For example, when the value of a task property is defined using a constant in a build script, then that property (which is-a provider) has a "fixed" value and its inputs include the build script.
 *     <p>When a provider has a fixed value, then the value of the provider can be assumed to have a constant ("fixed") value during execution time.
 *     In particular, this means that the fixed value can be written to the configuration cache entry rather than writing the provider itself, which is good for performance.</p>
 *     <p>There are other ways this concept could be used but that are not yet implemented,
 *     for example a provider with a fixed value could potentially memoize its value when queried at execution time, rather than calculating the value each time it is queried.</p>
 *     </li>
 *     <li>"changing", which means that the value is a function of some execution time inputs.
 *     For example, a task output property (whose value is a function of the task inputs) typically has a changing value.
 *     <p>When a provider has a changing value, then the provider must be written to the configuration cache, rather than writing its current value. This process is optimized by replacing any
 *     input providers that have a fixed value with that value, as above.
 *     </p>
 *     </li>
 * </ul>
 *
 * <p>See {@link org.gradle.api.internal.provider.ValueSupplier.ExecutionTimeValue}, which represents these states.</p>
 *
 * <p>The value itself might be "missing", which means there is no value available, or "broken", which means the calculation failed with some exception, or some object.
 * Behavior for broken values is currently provider implementation specific. Some implementations collect the failure and rethrow it each time the value is queried, and
 * some implementations retry the failed calculation.
 * </p>
 *
 * <p>Currently the "fixed" and "changing" states have definitions that refer to configuration time and execution time.
 * As these phases gradually become more interleaved, we might generalize these states so that the value is "changing" only until its inputs are known and all such inputs have a "fixed" value.
 * A provider whose inputs are only configuration time inputs would become fixed once the configuration of those inputs has completed. A provider whose inputs include a task output would become
 * fixed once the task has executed. It would become an error to query a provider whose value is still "changing".
 * </p>
 *
 * <h2>The value content</h2>
 *
 * <p>Each provider guarantees that the content of the value is in some particular state when the provider is queried.
 * Currently there are only two states that the various provider implementations can guarantee:
 * </p>
 *
 * <ul>
 *     <li>"any state". The provider will return the value and make no guarantees about the content. This might be used, for example, when generating IDE configuration files or models.
 *     The task that generates the configuration file might only care where some output file will end up and not care whether or not that file has been built yet.
 *     <p>When a provider returns the value in any state, then it will not carry any dependencies on the work nodes that produce the content (as the consumer doesn't care about the content, only the value).
 *     However, when the value itself is also calculated from work node outputs, then the provider will carry dependencies on the work nodes that produce the value (as the consumer does care about the value).
 *     </p>
 *     <p>In general, a provider that returns the value in any state will fail when it is queried before the value is known. This constraint isn't 100% implemented everywhere yet.</p>
 *     </li>
 *     <li>"usable". This is currently somewhat vague, but basically means that if the content is built by some work node, then that work node has already executed.
 *     <p>When a provider returns the value in a usable state, then it will carry dependencies on the work nodes that produce the content. When the value also happens to be calculated from work node outputs,
 *     then the provider will also carry dependencies on the work nodes that produce the value.</p>
 *     <p>In general, a provider that returns the value in a usable state will fail when it is queried before the value is known and the content produced. This constraint isn't 100% implemented everywhere yet.</p>
 *     </li>
 * </ul>
 *
 * <p>Additional states might be added later, for example, there might be provider implementations that return the "destroyed" content (ie after it has been cleaned/deleted/stopped/uninstalled),
 * or in "started" state (eg where the value represents an application that has been started and is ready to use, say for testing).
 * </p>
 *
 * <p>Like the value, the content may also be "fixed" or "changing" and these are somewhat independent. We end up with the following combinations:</p>
 *
 * <ul>
 *     <li>a fixed missing or broken value. Because there is no value, there is also no content.</li>
 *     <li>a fixed value with fixed content, for example a {@code Provider<String>} with a fixed value.</li>
 *     <li>a fixed value with changing content, for example a {@code Provider<RegularFile>} where the file path is hardcoded in a build script or plugin but the file content is built by a task.</li>
 *     <li>a changing value with changing content, for example a {@code Provider<RegularFile>} where the file path is calculated from some task's output file and whose content is built by a different task.</li>
 * </ul>
 *
 * <p>Currently there is no "changing value with fixed content", as there are no use cases for it yet. For now, if the value is changing then the content is also assumed to be changing.</p>
 *
 * <p>There are further optimizations that could be implemented with configuration caching. For example, when a work node has only fixed inputs, the node could be executed prior to writing the work graph to
 * the configuration cache, so that its outputs in turn become fixed. The node can then be discarded from the graph and replaced with its (now fixed) outputs.</p>
 */
public interface ProviderInternal<T> extends Provider<T>, ValueSupplier, TaskDependencyContainer, EvaluationContext.EvaluationOwner {
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

    @Override
    default <U, R> Provider<R> zip(Provider<U> right, BiFunction<? super T, ? super U, ? extends R> combiner) {
        return new BiProvider<>(null, this, right, combiner);
    }

    /**
     * Returns a provider that attaches the side effect to the values it produces.
     * <p>
     * The side effect is executed when the underlying value is accessed with methods such as
     * {@link Provider#get()}, {@link Provider#getOrNull()} and {@link Provider#getOrElse(Object)}.
     * The side effect will not be executed if the provider returns a missing value.
     * <p>
     * Multiple side effects can be attached to a provider, in which case they will be executed sequentially.
     * <p>
     * In case the provider is {@link #map transformed}, the value before the transform is captured for this side effect.
     * The new side effect for the captured value is then attached to the transformed provider.
     * This new "fixed" side effect will be executed when the resulting provider produces its values.
     */
    default ProviderInternal<T> withSideEffect(@Nullable SideEffect<? super T> sideEffect) {
        return WithSideEffectProvider.of(this, sideEffect);
    }
}
