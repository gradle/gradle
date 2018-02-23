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

package org.gradle.api.provider;

import org.gradle.api.Incubating;
import org.gradle.api.Transformer;
import org.gradle.api.tasks.Internal;
import org.gradle.internal.HasInternalProtocol;

import javax.annotation.Nullable;

/**
 * A container object that provides a value of a specific type. The value can be retrieved by the method {@link #get()} or {@link #getOrNull()}.
 *
 * <p>
 *  A provider may not always have a value available, for example when the value may not yet be known but will be known at some point in the future.
 *  When a value is not available, {@link #isPresent()} returns {@code false} and retrieving the value will fail with an exception.
 * </p>
 *
 * <p>
 *  A provider may not always provide the same value. Although there are no methods on this interface to change the value,
 *  the provider implementation may be mutable or use values from some changing source.
 * </p>
 *
 * <p>
 *  A provider may provide a value that is mutable and that changes over time.
 * </p>
 *
 * <p>
 *  A typical use of a provider is to pass values from one DSL element to another, e.g. from an extension to a task.
 *  Providers also allow expensive computations to be deferred until their value is actually needed, usually at task execution time.
 * </p>
 *
 * <p>
 *  For a provider whose value can be mutated, see {@link PropertyState}.
 * </p>
 *
 * <p>
 *  Do not use <code>Provider&lt;File&gt;</code>. Use {@link org.gradle.api.file.Directory} or {@link org.gradle.api.file.RegularFile} instead.
 * </p>
 *
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors. An instance of this class can be created through the factory methods {@link org.gradle.api.Project#provider(java.util.concurrent.Callable)} or {@link org.gradle.api.provider.ProviderFactory#provider(java.util.concurrent.Callable)}.
 *
 * @param <T> Type of value represented by provider
 * @since 4.0
 */
@Incubating
@HasInternalProtocol
public interface Provider<T> {

    /**
     * Returns the value of this provider if it has a value present, otherwise throws {@code java.lang.IllegalStateException}.
     *
     * @return the current value of this provider.
     * @throws IllegalStateException if there is no value present
     */
    T get();

    /**
     * Returns the value of this provider if it has a value present. Returns {@code null} a value is not available.
     *
     * @return the value or {@code null}
     */
    @Nullable
    @Internal
    T getOrNull();

    /**
     * Returns the value of this provider if it has a value present. Returns the given default value if a value is not available.
     *
     * @return the value or the default value.
     * @since 4.3
     */
    T getOrElse(T defaultValue);

    /**
     * Returns a new {@link Provider} whose value is the value of this provider transformed using the given function.
     *
     * <p>The new provider will be live, so that each time it is queried, it queries this provider and applies the transformation to the result. Whenever this provider has no value, the new provider will also have no value.
     *
     * <p>Note that the new provider may cache the result of the transformations and so there is no guarantee that the transformer is called on every query of the new provider. The new provider will apply the transformation lazily, and calculate the value for the new provider when queried.
     *
     * @param transformer The transformer to apply to values. Should not return {@code null}.
     * @since 4.3
     */
    <S> Provider<S> map(Transformer<? extends S, ? super T> transformer);

    /**
     * Returns {@code true} if there is a value present, otherwise {@code false}.
     *
     * @return {@code true} if there is a value present, otherwise {@code false}
     */
    @Internal
    boolean isPresent();
}
