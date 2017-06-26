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
import org.gradle.api.tasks.Internal;

/**
 * A container object which provides a value of a specific type. The value can be retrieved by the method {@code get()} or {@code getOrNull()}.
 * <p>
 * <b>Note:</b> This interface is not intended for implementation by build script or plugin authors. An instance of this class can be created
 * through the factory methods {@link org.gradle.api.Project#provider(java.util.concurrent.Callable)} or
 * {@link org.gradle.api.provider.ProviderFactory#provider(java.util.concurrent.Callable)}.
 *
 * @param <T> Type of value represented by provider
 * @since 4.0
 */
@Incubating
public interface Provider<T> {

    /**
     * If a value is present in this provider, returns the value, otherwise throws {@code java.lang.IllegalStateException}.
     *
     * @return Value
     * @throws IllegalStateException if there is no value present
     */
    T get();

    /**
     * Returns the value if present in this provider. Returns null if value is not available.
     *
     * @return Value
     */
    @Internal
    T getOrNull();

    /**
     * Returns {@code true} if there is a value present, otherwise {@code false}.
     *
     * @return {@code true} if there is a value present, otherwise {@code false}
     */
    @Internal
    boolean isPresent();
}
