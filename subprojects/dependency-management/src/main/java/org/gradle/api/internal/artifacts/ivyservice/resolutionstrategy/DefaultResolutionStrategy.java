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

package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ComponentSelection;
import org.gradle.api.artifacts.ComponentSelectionRules;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.dsl.ModuleVersionSelectorParsers;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencySubstitutions;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.locking.NoOpDependencyLockingProvider;
import org.gradle.internal.rules.SpecRuleAction;
import org.gradle.internal.typeconversion.NormalizedTimeUnit;
import org.gradle.internal.typeconversion.TimeUnitsParser;
import org.gradle.vcs.internal.VcsResolver;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.gradle.api.internal.artifacts.configurations.MutationValidator.MutationType.STRATEGY;

public class DefaultResolutionStrategy implements ResolutionStrategyInternal {
    private final Set<Object> forcedModules = new LinkedHashSet<Object>();
    private Set<ModuleVersionSelector> parsedForcedModules;
    private ConflictResolution conflictResolution = ConflictResolution.latest;
    private final DefaultComponentSelectionRules componentSelectionRules;

    private final DefaultCachePolicy cachePolicy;
    private final DependencySubstitutionsInternal dependencySubstitutions;
    private final DependencySubstitutionRules globalDependencySubstitutionRules;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final VcsResolver vcsResolver;
    private final ComponentSelectorConverter componentSelectorConverter;
    private final DependencyLockingProvider dependencyLockingProvider;
    private MutationValidator mutationValidator = MutationValidator.IGNORE;

    private boolean dependencyLockingEnabled = false;
    private boolean assumeFluidDependencies;
    private SortOrder sortOrder = SortOrder.DEFAULT;
    private static final String ASSUME_FLUID_DEPENDENCIES = "org.gradle.resolution.assumeFluidDependencies";

    public DefaultResolutionStrategy(DependencySubstitutionRules globalDependencySubstitutionRules, VcsResolver vcsResolver, ComponentIdentifierFactory componentIdentifierFactory, ImmutableModuleIdentifierFactory moduleIdentifierFactory, ComponentSelectorConverter componentSelectorConverter, DependencyLockingProvider dependencyLockingProvider) {
        this(new DefaultCachePolicy(), DefaultDependencySubstitutions.forResolutionStrategy(componentIdentifierFactory, moduleIdentifierFactory), globalDependencySubstitutionRules, vcsResolver, moduleIdentifierFactory, componentSelectorConverter, dependencyLockingProvider);
    }

    DefaultResolutionStrategy(DefaultCachePolicy cachePolicy, DependencySubstitutionsInternal dependencySubstitutions, DependencySubstitutionRules globalDependencySubstitutionRules, VcsResolver vcsResolver, ImmutableModuleIdentifierFactory moduleIdentifierFactory, ComponentSelectorConverter componentSelectorConverter, DependencyLockingProvider dependencyLockingProvider) {
        this.cachePolicy = cachePolicy;
        this.dependencySubstitutions = dependencySubstitutions;
        this.globalDependencySubstitutionRules = globalDependencySubstitutionRules;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.componentSelectionRules = new DefaultComponentSelectionRules(moduleIdentifierFactory);
        this.vcsResolver = vcsResolver;
        this.componentSelectorConverter = componentSelectorConverter;
        this.dependencyLockingProvider = dependencyLockingProvider;
        // This is only used for testing purposes so we can test handling of fluid dependencies without adding dependency substitution rule
        assumeFluidDependencies = Boolean.getBoolean(ASSUME_FLUID_DEPENDENCIES);
    }

    @Override
    public void setMutationValidator(MutationValidator validator) {
        mutationValidator = validator;
        cachePolicy.setMutationValidator(validator);
        componentSelectionRules.setMutationValidator(validator);
        dependencySubstitutions.setMutationValidator(validator);
    }

    @Override
    public Set<ModuleVersionSelector> getForcedModules() {
        if (parsedForcedModules == null) {
            parsedForcedModules = ModuleVersionSelectorParsers.multiParser().parseNotation(forcedModules);
        }
        return Collections.unmodifiableSet(parsedForcedModules);
    }

    @Override
    public ResolutionStrategy failOnVersionConflict() {
        mutationValidator.validateMutation(STRATEGY);
        this.conflictResolution = ConflictResolution.strict;
        return this;
    }

    @Override
    public void preferProjectModules() {
        conflictResolution = ConflictResolution.preferProjectModules;
    }

    @Override
    public ResolutionStrategy activateDependencyLocking() {
        mutationValidator.validateMutation(STRATEGY);
        dependencyLockingEnabled = true;
        return this;
    }

    @Override
    public void sortArtifacts(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public SortOrder getSortOrder() {
        return sortOrder;
    }

    @Override
    public ConflictResolution getConflictResolution() {
        return this.conflictResolution;
    }

    @Override
    public DefaultResolutionStrategy force(Object... moduleVersionSelectorNotations) {
        mutationValidator.validateMutation(STRATEGY);
        parsedForcedModules = null;
        Collections.addAll(forcedModules, moduleVersionSelectorNotations);
        return this;
    }

    @Override
    public ResolutionStrategy eachDependency(Action<? super DependencyResolveDetails> rule) {
        mutationValidator.validateMutation(STRATEGY);
        dependencySubstitutions.allWithDependencyResolveDetails(rule, componentSelectorConverter);
        return this;
    }

    @Override
    public Action<DependencySubstitution> getDependencySubstitutionRule() {
        Set<ModuleVersionSelector> forcedModules = getForcedModules();
        Action<DependencySubstitution> moduleForcingResolveRule = Cast.uncheckedCast(forcedModules.isEmpty() ? Actions.doNothing() : new ModuleForcingResolveRule(forcedModules));
        Action<DependencySubstitution> localDependencySubstitutionsAction = this.dependencySubstitutions.getRuleAction();
        Action<DependencySubstitution> globalDependencySubstitutionRulesAction = globalDependencySubstitutionRules.getRuleAction();
        //noinspection unchecked
        return Actions.composite(moduleForcingResolveRule, localDependencySubstitutionsAction, globalDependencySubstitutionRulesAction);
    }

    @Override
    public void assumeFluidDependencies() {
        assumeFluidDependencies = true;
    }

    @Override
    public boolean resolveGraphToDetermineTaskDependencies() {
        return assumeFluidDependencies || dependencySubstitutions.hasRules() || globalDependencySubstitutionRules.hasRules() || vcsResolver.hasRules();
    }

    @Override
    public DefaultResolutionStrategy setForcedModules(Object... moduleVersionSelectorNotations) {
        mutationValidator.validateMutation(STRATEGY);
        this.forcedModules.clear();
        force(moduleVersionSelectorNotations);
        return this;
    }

    @Override
    public DefaultCachePolicy getCachePolicy() {
        return cachePolicy;
    }

    @Override
    public void cacheDynamicVersionsFor(int value, String units) {
        NormalizedTimeUnit timeUnit = new TimeUnitsParser().parseNotation(units, value);
        cacheDynamicVersionsFor(timeUnit.getValue(), timeUnit.getTimeUnit());
    }

    @Override
    public void cacheDynamicVersionsFor(int value, TimeUnit units) {
        this.cachePolicy.cacheDynamicVersionsFor(value, units);
    }

    @Override
    public void cacheChangingModulesFor(int value, String units) {
        NormalizedTimeUnit timeUnit = new TimeUnitsParser().parseNotation(units, value);
        cacheChangingModulesFor(timeUnit.getValue(), timeUnit.getTimeUnit());
    }

    @Override
    public void cacheChangingModulesFor(int value, TimeUnit units) {
        this.cachePolicy.cacheChangingModulesFor(value, units);
    }

    @Override
    public ComponentSelectionRulesInternal getComponentSelection() {
        return componentSelectionRules;
    }

    @Override
    public ResolutionStrategy componentSelection(Action<? super ComponentSelectionRules> action) {
        action.execute(componentSelectionRules);
        return this;
    }

    @Override
    public DependencySubstitutionsInternal getDependencySubstitution() {
        return dependencySubstitutions;
    }

    @Override
    public ResolutionStrategy dependencySubstitution(Action<? super DependencySubstitutions> action) {
        action.execute(dependencySubstitutions);
        return this;
    }

    @Override
    public DefaultResolutionStrategy copy() {
        DefaultResolutionStrategy out = new DefaultResolutionStrategy(cachePolicy.copy(), dependencySubstitutions.copy(), globalDependencySubstitutionRules, vcsResolver, moduleIdentifierFactory, componentSelectorConverter, dependencyLockingProvider);

        if (conflictResolution == ConflictResolution.strict) {
            out.failOnVersionConflict();
        } else if (conflictResolution == ConflictResolution.preferProjectModules) {
            out.preferProjectModules();
        }
        out.setForcedModules(forcedModules);
        for (SpecRuleAction<? super ComponentSelection> ruleAction : componentSelectionRules.getRules()) {
            out.getComponentSelection().addRule(ruleAction);
        }
        if (isDependencyLockingEnabled()) {
            out.activateDependencyLocking();
        }
        return out;
    }

    @Override
    public DependencyLockingProvider getDependencyLockingProvider() {
        if (dependencyLockingEnabled) {
            return dependencyLockingProvider;
        } else {
            return NoOpDependencyLockingProvider.getInstance();
        }
    }

    @Override
    public boolean isDependencyLockingEnabled() {
        return dependencyLockingEnabled;
    }
}
