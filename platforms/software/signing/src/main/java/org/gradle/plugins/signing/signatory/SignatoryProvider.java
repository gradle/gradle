/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.signing.signatory;

import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.plugins.signing.SigningExtension;

/**
 * <p>Provides implementations of signatory implementations for a project.</p>
 *
 * @param <T> The specific {@link Signatory} subtype
 */
public interface SignatoryProvider<T extends Signatory> {

    /**
     * Evaluates the given DSL-containing-closure as signatory configuration.
     *
     * @param settings The signing settings for the project the configure is happening for
     */
    void configure(SigningExtension settings, Closure closure);

    /**
     * <p>Attempts to create a signatory for the project that will be used everywhere something is to be signed and an explicit signatory has not been set (for the task/operation).</p>
     *
     * <p>This may be called multiple times and the implementer is free to return a different instance if the project state has changed in someway that influences the default signatory.</p>
     *
     * @param project The project which the signatory is for
     * @return The signatory, or {@code null} if there is insufficient information available to create one.
     */
    T getDefaultSignatory(Project project);

    /**
     * Retrieves the signatory with the given name.
     *
     * @param name The desired signatory's name.
     * @return The signatory with the given name if found, or {@code null} if no signatory is found with this name.
     */
    T getSignatory(String name);
}
