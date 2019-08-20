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

import org.gradle.internal.HasInternalProtocol;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.util.List;

/**
 * Answers the question why a component was selected during the dependency resolution.
 *
 * @since 1.3
 */
@UsedByScanPlugin
@HasInternalProtocol
public interface ComponentSelectionReason {

    /**
     * Informs whether the component was forced. Users can force components via {@link org.gradle.api.artifacts.ResolutionStrategy} or when declaring dependencies (see {@link
     * org.gradle.api.artifacts.dsl.DependencyHandler}).
     */
    boolean isForced();

    /**
     * Informs whether the component was selected by conflict resolution. For more information about Gradle's conflict resolution please refer to the user manual. {@link
     * org.gradle.api.artifacts.ResolutionStrategy} contains information about conflict resolution and includes means to configure it.
     */
    boolean isConflictResolution();

    /**
     * Informs whether the component was selected by the dependency substitution rule. Users can configure dependency substitution rules via {@link
     * org.gradle.api.artifacts.ResolutionStrategy#getDependencySubstitution()}
     *
     * @since 1.4
     */
    boolean isSelectedByRule();

    /**
     * Informs whether the component is the requested selection of all dependency declarations, and was not replaced for some reason, such as conflict resolution.
     *
     * @since 1.11
     */
    boolean isExpected();

    /**
     * Informs whether the selected component is a project substitute from a build participating in in a composite build.
     *
     * @since 4.5
     */
    boolean isCompositeSubstitution();

    /**
     * Informs whether the selected component version has been influenced by a dependency constraint.
     *
     * @return true if a dependency constraint influenced the selection of this component
     *
     * @since 4.6
     */
    boolean isConstrained();

    /**
     * Returns a human-consumable description of this selection reason.
     *
     * @deprecated Use {@link #getDescriptions()} instead
     */
    @Deprecated
    String getDescription();

    /**
     * Returns a list of descriptions of the causes that led to the selection of this component.
     *
     * @return the list of descriptions.
     *
     * @since 4.6
     */
    List<ComponentSelectionDescriptor> getDescriptions();
}
