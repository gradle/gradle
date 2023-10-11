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

import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.internal.ImmutableActionSet;

public interface ResolutionStrategyInternal extends ResolutionStrategy {
    /**
     * Discard any configuration state that is not required after graph resolution has been attempted.
     */
    void discardStateRequiredForGraphResolution();

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
     * @return the dependency substitution rule (may aggregate multiple rules)
     */
    ImmutableActionSet<DependencySubstitutionInternal> getDependencySubstitutionRule();

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

    @Override
    DependencySubstitutionsInternal getDependencySubstitution();

    /**
     * @return the version selection rules object
     */
    @Override
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
     * Returns the dependency locking provider linked to this resolution strategy.
     *
     * @return dependency locking provider
     */
    DependencyLockingProvider getDependencyLockingProvider();

    /**
     * Indicates if dependency locking is enabled.
     *
     * @return {@code true} if dependency locking is enabled, {@code false} otherwise
     */
    boolean isDependencyLockingEnabled();

    /**
     * Confirms that an unlocked configuration has been resolved.
     * This allows the lock state for said configuration to be dropped if it existed before.
     *
     * @param configurationName the unlocked configuration
     */
    void confirmUnlockedConfigurationResolved(String configurationName);

    CapabilitiesResolutionInternal getCapabilitiesResolutionRules();

    boolean isFailingOnDynamicVersions();

    boolean isFailingOnChangingVersions();

    boolean isDependencyVerificationEnabled();

    void setReturnAllVariants(boolean returnAllVariants);

    boolean getReturnAllVariants();
}
