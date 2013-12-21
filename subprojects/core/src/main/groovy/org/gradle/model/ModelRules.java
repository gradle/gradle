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

package org.gradle.model;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.internal.Factory;

/**
 * A service for registering model rules.
 *
 * Plugins can inject an instance of this.
 */
@Incubating
public interface ModelRules {
    /**
     * Registers a model object under the given path.
     */
    <T> void register(String path, T model);

    /**
     * Registers a model object under the given path. The provided factory will be used to create the model object when it is required.
     */
    <T> void register(String path, Class<T> type, Factory<? extends T> model);

    /**
     * Registers an action that configures the model object at the given path. The provided action will be executed against the model object when
     * the model object is required.
     */
    <T> void config(String path, Action<T> action);

    /**
     * Registers a rule. The rule is inspected to determine its inputs and outputs.
     *
     * @see ModelRule For details.
     */
    void rule(ModelRule rule);

    /**
     * Removes the model object from the given path.
     */
    void remove(String path);
}
