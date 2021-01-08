/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ExternalModuleDependency;

/**
 * Allows the API and implementation dependencies of a library to be specified.
 *
 * @since 4.6
 */
public interface LibraryDependencies extends ComponentDependencies {
    /**
     * Adds an API dependency to this library. An API dependency is made visible to consumers that are compiled against this component.
     *
     * @param notation The dependency notation, as per {@link org.gradle.api.artifacts.dsl.DependencyHandler#create(Object)}.
     */
    void api(Object notation);

    /**
     * Adds an API dependency to this library. An API dependency is made visible to consumers that are compiled against this component.
     *
     * @param notation The dependency notation, as per {@link org.gradle.api.artifacts.dsl.DependencyHandler#create(Object)}.
     * @param action The action to run to configure the dependency.
     */
    void api(Object notation, Action<? super ExternalModuleDependency> action);
}
