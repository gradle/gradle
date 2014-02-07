/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.artifacts.result;

import org.gradle.api.Incubating;

/**
 * Answers the question why a component was selected during the dependency resolution.
 *
 * @since 1.3
 */
@Incubating
public interface ComponentSelectionReason {

    /**
     * Informs whether the component was forced.
     * Users can force components via {@link org.gradle.api.artifacts.ResolutionStrategy}
     * or when declaring dependencies (see {@link org.gradle.api.artifacts.dsl.DependencyHandler}).
     */
    boolean isForced();

    /**
     * Informs whether the component was selected by conflict resolution.
     * For more information about Gradle's conflict resolution please refer to the user
     * guide. {@link org.gradle.api.artifacts.ResolutionStrategy} contains information
     * about conflict resolution and includes means to configure it.
     */
    boolean isConflictResolution();

    /**
     * Informs whether the component was selected by the dependency resolve rule.
     * Users can configure dependency resolve rules via {@link org.gradle.api.artifacts.ResolutionStrategy#eachDependency(org.gradle.api.Action)}
     *
     * @since 1.4
     */
    boolean isSelectedByRule();

    /**
     * Informs whether the component is an expected selection.
     *
     * @return Flag
     * @since 1.11
     */
    boolean isExpected();

    /**
     * Returns a human-consumable description of this selection reason.
     */
    String getDescription();
}
