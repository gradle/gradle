/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.model;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * A container of data models exposed by a project.
 *
 * @since 9.1.0
 */
@Incubating
@ServiceScope(Scope.Project.class)
public interface ModelContainer {

    /**
     * Register a data model of the given type in this container.
     * <p>
     * Gradle will create an instance of the model type and configure it using the provided action.
     *
     * @param type the type of the model to register
     * @param configureAction the action to configure the model instance
     *
     * @param <T> the type of the model
     *
     * @since 9.1.0
     */
    <T> void register(Class<T> type, Action<T> configureAction);

}
