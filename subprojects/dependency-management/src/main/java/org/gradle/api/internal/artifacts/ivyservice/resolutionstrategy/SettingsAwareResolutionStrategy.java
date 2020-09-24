/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.CapabilitiesResolution;
import org.gradle.api.artifacts.ComponentSelectionRules;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal;
import org.gradle.initialization.DependencyResolutionManagementInternal;

import java.util.Set;
import java.util.concurrent.TimeUnit;

public class SettingsAwareResolutionStrategy implements ResolutionStrategyInternal {
    private final ResolutionStrategyInternal delegate;
    private final DependencyResolutionManagementInternal dependencyResolutionManagement;

    private boolean applySettingsStrategy = true;

    public SettingsAwareResolutionStrategy(ResolutionStrategyInternal delegate, DependencyResolutionManagementInternal dependencyResolutionManagement) {
        this.delegate = delegate;
        this.dependencyResolutionManagement = dependencyResolutionManagement;
    }

    private void maybeApplySettingsStrategy() {
        if (applySettingsStrategy) {
            dependencyResolutionManagement.applyResolutionStrategy(delegate);
            applySettingsStrategy = false;
        }
    }

    @Override
    public CachePolicy getCachePolicy() {
        maybeApplySettingsStrategy();
        return delegate.getCachePolicy();
    }

    @Override
    public ConflictResolution getConflictResolution() {
        maybeApplySettingsStrategy();
        return delegate.getConflictResolution();
    }

    @Override
    public Action<DependencySubstitution> getDependencySubstitutionRule() {
        maybeApplySettingsStrategy();
        return delegate.getDependencySubstitutionRule();
    }

    @Override
    public void assumeFluidDependencies() {
        mutatedByUser();
        delegate.assumeFluidDependencies();
    }

    private void mutatedByUser() {
        applySettingsStrategy = false;
    }

    @Override
    public boolean resolveGraphToDetermineTaskDependencies() {
        maybeApplySettingsStrategy();
        return delegate.resolveGraphToDetermineTaskDependencies();
    }

    @Override
    public SortOrder getSortOrder() {
        maybeApplySettingsStrategy();
        return delegate.getSortOrder();
    }

    @Override
    public DependencySubstitutionsInternal getDependencySubstitution() {
        maybeApplySettingsStrategy();
        return delegate.getDependencySubstitution();
    }

    @Override
    public ComponentSelectionRulesInternal getComponentSelection() {
        maybeApplySettingsStrategy();
        return delegate.getComponentSelection();
    }

    @Override
    public ResolutionStrategyInternal copy() {
        return new SettingsAwareResolutionStrategy(delegate.copy(), dependencyResolutionManagement);
    }

    @Override
    public void setMutationValidator(MutationValidator action) {
        delegate.setMutationValidator(action);
    }

    @Override
    public DependencyLockingProvider getDependencyLockingProvider() {
        maybeApplySettingsStrategy();
        return delegate.getDependencyLockingProvider();
    }

    @Override
    public boolean isDependencyLockingEnabled() {
        maybeApplySettingsStrategy();
        return delegate.isDependencyLockingEnabled();
    }

    @Override
    public CapabilitiesResolutionInternal getCapabilitiesResolutionRules() {
        maybeApplySettingsStrategy();
        return delegate.getCapabilitiesResolutionRules();
    }

    @Override
    public boolean isFailingOnDynamicVersions() {
        maybeApplySettingsStrategy();
        return delegate.isFailingOnDynamicVersions();
    }

    @Override
    public boolean isFailingOnChangingVersions() {
        maybeApplySettingsStrategy();
        return delegate.isFailingOnChangingVersions();
    }

    @Override
    public boolean isDependencyVerificationEnabled() {
        maybeApplySettingsStrategy();
        return delegate.isDependencyVerificationEnabled();
    }

    @Override
    public ResolutionStrategy failOnVersionConflict() {
        mutatedByUser();
        delegate.failOnVersionConflict();
        return this;
    }

    @Override
    @Incubating
    public ResolutionStrategy failOnDynamicVersions() {
        mutatedByUser();
        delegate.failOnDynamicVersions();
        return this;
    }

    @Override
    @Incubating
    public ResolutionStrategy failOnChangingVersions() {
        mutatedByUser();
        delegate.failOnChangingVersions();
        return this;
    }

    @Override
    @Incubating
    public ResolutionStrategy failOnNonReproducibleResolution() {
        mutatedByUser();
        delegate.failOnNonReproducibleResolution();
        return this;
    }

    @Override
    public void preferProjectModules() {
        mutatedByUser();
        delegate.preferProjectModules();
    }

    @Override
    public ResolutionStrategy activateDependencyLocking() {
        mutatedByUser();
        delegate.activateDependencyLocking();
        return this;
    }

    @Override
    @Incubating
    public ResolutionStrategy deactivateDependencyLocking() {
        mutatedByUser();
        delegate.deactivateDependencyLocking();
        return this;
    }

    @Override
    @Incubating
    public ResolutionStrategy disableDependencyVerification() {
        mutatedByUser();
        delegate.disableDependencyVerification();
        return this;
    }

    @Override
    @Incubating
    public ResolutionStrategy enableDependencyVerification() {
        mutatedByUser();
        delegate.enableDependencyVerification();
        return this;
    }

    @Override
    public ResolutionStrategy force(Object... moduleVersionSelectorNotations) {
        mutatedByUser();
        delegate.force(moduleVersionSelectorNotations);
        return this;
    }

    @Override
    public ResolutionStrategy setForcedModules(Object... moduleVersionSelectorNotations) {
        mutatedByUser();
        return delegate.setForcedModules(moduleVersionSelectorNotations);
    }

    @Override
    public Set<ModuleVersionSelector> getForcedModules() {
        maybeApplySettingsStrategy();
        return delegate.getForcedModules();
    }

    @Override
    public ResolutionStrategy eachDependency(Action<? super DependencyResolveDetails> rule) {
        mutatedByUser();
        delegate.eachDependency(rule);
        return this;
    }

    @Override
    public void cacheDynamicVersionsFor(int value, String units) {
        mutatedByUser();
        delegate.cacheDynamicVersionsFor(value, units);
    }

    @Override
    public void cacheDynamicVersionsFor(int value, TimeUnit units) {
        mutatedByUser();
        delegate.cacheDynamicVersionsFor(value, units);
    }

    @Override
    public void cacheChangingModulesFor(int value, String units) {
        mutatedByUser();
        delegate.cacheChangingModulesFor(value, units);
    }

    @Override
    public void cacheChangingModulesFor(int value, TimeUnit units) {
        mutatedByUser();
        delegate.cacheChangingModulesFor(value, units);
    }

    @Override
    public ResolutionStrategy componentSelection(Action<? super ComponentSelectionRules> action) {
        mutatedByUser();
        delegate.componentSelection(action);
        return this;
    }

    @Override
    public ResolutionStrategy dependencySubstitution(Action<? super DependencySubstitutions> action) {
        mutatedByUser();
        delegate.dependencySubstitution(action);
        return this;
    }

    @Override
    public void sortArtifacts(SortOrder sortOrder) {
        mutatedByUser();
        delegate.sortArtifacts(sortOrder);
    }

    @Override
    @Incubating
    public ResolutionStrategy capabilitiesResolution(Action<? super CapabilitiesResolution> action) {
        mutatedByUser();
        delegate.capabilitiesResolution(action);
        return this;
    }

    @Override
    @Incubating
    public CapabilitiesResolution getCapabilitiesResolution() {
        maybeApplySettingsStrategy();
        return delegate.getCapabilitiesResolution();
    }
}
