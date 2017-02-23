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

import org.gradle.api.Buildable;
import org.gradle.api.Incubating;
import org.gradle.api.Nullable;

import java.util.concurrent.Callable;

/**
 * A container object which provides a value of a specific type. The value, retrieved by the method {@code get()},
 * may need to be calculated or lazily evaluated and can return a null or non-null value.
 * <p>
 * You can create a {@code Provider} instance using the following methods:
 * <ol>
 * <li>{@link org.gradle.api.Project#defaultProvider(Class)}</li>
 * <li>{@link org.gradle.api.Project#provider(Object)}</li>
 * <li>{@link org.gradle.api.Project#provider(Callable)}</li>
 * </ol>
 *
 * @param <T> Type of value represented by provider
 * @since 3.5
 */
@Incubating
public interface Provider<T> extends Buildable {

    /**
     * Returns the value defined for the provider.
     *
     * @return Value
     */
    @Nullable
    T get();

    /**
     * Sets the tasks which build the files of this provider.
     *
     * @param tasks The tasks. These are evaluated as per {@link org.gradle.api.Task#dependsOn(Object...)}.
     * @return this
     */
    Provider<T> builtBy(Object... tasks);
}
