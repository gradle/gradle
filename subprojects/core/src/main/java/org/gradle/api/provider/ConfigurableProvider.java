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

import java.util.Set;

/**
 * A mutable {@code Provider} representation.
 *
 * @param <T> Type of value represented by provider
 * @since 3.5
 */
@Incubating
public interface ConfigurableProvider<T> extends Provider {

    /**
     * Returns the set of tasks which build the value of this provider.
     *
     * @return The set. Returns an empty set when there are no such tasks.
     */
    Set<Object> getBuiltBy();

    /**
     * Sets the tasks which build the value of this provider.
     *
     * @param tasks The tasks. These are evaluated as per {@link org.gradle.api.Task#dependsOn(Object...)}.
     * @return this
     */
    ConfigurableProvider setBuiltBy(Iterable<?> tasks);

    /**
     * Sets the tasks which build the value of this provider.
     *
     * @param tasks The tasks. These are evaluated as per {@link org.gradle.api.Task#dependsOn(Object...)}.
     * @return this
     */
    ConfigurableProvider<T> builtBy(Object... tasks);
}
