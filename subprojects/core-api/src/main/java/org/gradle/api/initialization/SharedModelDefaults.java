/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.initialization;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Shared model defaults for configuring software types.  Defaults apply initial values to the model objects of software types.
 * When a software type plugin is applied to a project, the model object for the software type will be pre-configured with the values
 * set in the default.
 *
 * @since 8.10
 */
@Incubating
@ServiceScope(Scope.Build.class)
public interface SharedModelDefaults {
    /**
     * Adds a model default for the software type specified by the given name.
     *
     * @param name the name of the software type
     * @param publicType the public type of the software type
     * @param configureAction the action to configure the software type
     *
     * @since 8.10
     */
    <T> void add(String name, Class<T> publicType, Action<? super T> configureAction);
}
