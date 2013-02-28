/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * A Domain Object that accumulates configuration actions and executes them lazily at a later time.
 * @param <T> The Domain Object type
 */
@Incubating
public interface DeferredConfigurable<T> {
    /**
     * Add a configuration action for later execution.
     * @param action The configuration action
     * @throws IllegalStateException if already configured by {@link #configureNow()}.
     */
    void configureLater(Action<? super T> action);

    /**
     * Evaluate all configuration actions if not already configured, and return the domain object.
     * @return The configured domain object
     * @throws org.gradle.api.InvalidUserDataException if any of the configuration actions fails.
     */
    T configureNow();
}
