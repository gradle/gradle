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
package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.cache.ResolutionRules;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal;

public interface ResolutionStrategyInternal extends ResolutionStrategy {

    /**
     * Gets the current expiry policy for dynamic revisions.
     *
     * @return the expiry policy
     */
    CachePolicy getCachePolicy();

    /**
     * Until the feature 'settles' and we receive more feedback, it's internal
     *
     * @return conflict resolution
     */
    ConflictResolution getConflictResolution();

    /**
     * The nascent DSL for cache control, and possibly other per-module resolution overrides
     * @return the resolution rules
     */
    ResolutionRules getResolutionRules();

    /**
     * @return the dependency substitution rule (may aggregate multiple rules)
     */
    Action<DependencySubstitution> getDependencySubstitutionRule();

    /**
     * Used by tests to validate behaviour of the 'task graph modified' state
     */
    void assumeFluidDependencies();

    /**
     * Should the configuration be fully resolved to determine the task dependencies?
     * If not, we do a shallow 'resolve' of SelfResolvingDependencies only.
     */
    boolean resolveGraphToDetermineTaskDependencies();

    SortOrder getSortOrder();

    DependencySubstitutionsInternal getDependencySubstitution();

    /**
     * @return the version selection rules object
     */
    ComponentSelectionRulesInternal getComponentSelection();

    /**
     * @return copy of this resolution strategy. See the contract of {@link org.gradle.api.artifacts.Configuration#copy()}.
     */
    ResolutionStrategyInternal copy();

    /**
     * Sets the validator to invoke before mutation. Any exception thrown by the action will veto the mutation.
     */
    void setMutationValidator(MutationValidator action);

    /**
     * Specifies the ordering for resolved artifacts. Options are:
     * <ul>
     * <li>{@link SortOrder#DEFAULT} : Don't specify the sort order. Gradle will provide artifacts in the default order.</li>
     * <li>{@link SortOrder#CONSUMER_FIRST} : Artifacts for a consuming component should appear before artifacts for it's dependents.</li>
     * <li>{@link SortOrder#DEPENDENT_FIRST} : Artifacts for a consuming component should appear after artifacts for it's dependents.</li>
     * </ul>
     * A best attempt will be made to sort artifacts according the supplied {@link SortOrder}, but no guarantees will be made in the presence of dependency cycles.
     *
     * NOTE: For a particular Gradle version, artifact ordering will be consistent. Multiple resolves for the same inputs will result in the
     * same outputs in the same order.
     */
    void sortArtifacts(SortOrder sortOrder);

    /**
     * Defines the sort order for components and artifacts produced by the configuration.
     *
     * @see #sortArtifacts(SortOrder)
     */
    enum SortOrder {
        DEFAULT, CONSUMER_FIRST, DEPENDENT_FIRST
    }
}
