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
 * Allows the implementation dependencies of a component to be specified.
 *
 * @since 4.6
 */
public interface ComponentDependencies {
    /**
     * Adds an implementation dependency to this component. An implementation dependency is not visible to consumers that are compiled against this component.
     *
     * @param notation The dependency notation, as per {@link org.gradle.api.artifacts.dsl.DependencyHandler#create(Object)}.
     */
    void implementation(Object notation);

    /**
     * Adds an implementation dependency to this component. An implementation dependency is not visible to consumers that are compiled against this component.
     *
     * @param notation The dependency notation, as per {@link org.gradle.api.artifacts.dsl.DependencyHandler#create(Object)}.
     * @param action The action to run to configure the dependency.
     */
    void implementation(Object notation, Action<? super ExternalModuleDependency> action);
}
