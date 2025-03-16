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
import org.gradle.api.artifacts.CapabilitiesResolution;
import org.gradle.api.artifacts.ComponentSelection;
import org.gradle.api.artifacts.ComponentSelectionRules;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.configurations.CachePolicy;
import org.gradle.api.internal.artifacts.dsl.ModuleVersionSelectorParsers;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.rules.SpecRuleAction;
import org.gradle.internal.typeconversion.NormalizedTimeUnit;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.TimeUnitsParser;
import org.gradle.vcs.internal.VcsResolver;

import javax.inject.Inject;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.gradle.api.internal.artifacts.configurations.MutationValidator.MutationType.STRATEGY;

public class DefaultResolutionStrategy implements ResolutionStrategyInternal {

    private static final String ASSUME_FLUID_DEPENDENCIES = "org.gradle.resolution.assumeFluidDependencies";
    private static final NotationParser<Object, Set<ModuleVersionSelector>> FORCED_MODULES_PARSER = ModuleVersionSelectorParsers.multiParser("force()");

    private final Set<Object> forcedModules = new LinkedHashSet<>();
    private Set<ModuleVersionSelector> parsedForcedModules;
    private ConflictResolution conflictResolution = ConflictResolution.latest;
    private final DefaultComponentSelectionRules componentSelectionRules;

    private final CachePolicy cachePolicy;
    private final DependencySubstitutionsInternal dependencySubstitutions;
    private final GlobalDependencyResolutionRules globalDependencySubstitutionRules;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final VcsResolver vcsResolver;
    private final ComponentSelectorConverter componentSelectorConverter;
    private final DependencyLockingProvider dependencyLockingProvider;
    private final CapabilitiesResolutionInternal capabilitiesResolution;
    private final ObjectFactory objectFactory;
    private MutationValidator mutationValidator = MutationValidator.IGNORE;

    private boolean dependencyLockingEnabled = false;
    private boolean assumeFluidDependencies;
    private SortOrder sortOrder = SortOrder.DEFAULT;
    private boolean failOnDynamicVersions;
    private boolean failOnChangingVersions;
    private boolean verifyDependencies = true;
    private final Property<Boolean> useGlobalDependencySubstitutionRules;
    private boolean selectableVariantResults = false;
    private boolean keepStateRequiredForGraphResolution = false;

    @Inject
    public DefaultResolutionStrategy(
        CachePolicy cachePolicy,
        DependencySubstitutionsInternal dependencySubstitutions,
        GlobalDependencyResolutionRules globalDependencySubstitutionRules,
        VcsResolver vcsResolver,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        ComponentSelectorConverter componentSelectorConverter,
        DependencyLockingProvider dependencyLockingProvider,
        CapabilitiesResolutionInternal capabilitiesResolution,
        ObjectFactory objectFactory
    ) {
        this.cachePolicy = cachePolicy;
        this.dependencySubstitutions = dependencySubstitutions;
        this.globalDependencySubstitutionRules = globalDependencySubstitutionRules;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.componentSelectionRules = new DefaultComponentSelectionRules(moduleIdentifierFactory);
        this.vcsResolver = vcsResolver;
        this.componentSelectorConverter = componentSelectorConverter;
        this.dependencyLockingProvider = dependencyLockingProvider;
        this.capabilitiesResolution = capabilitiesResolution;
        this.objectFactory = objectFactory;
        this.useGlobalDependencySubstitutionRules = objectFactory.property(Boolean.class).convention(true);
        // This is only used for testing purposes so we can test handling of fluid dependencies without adding dependency substitution rule
        assumeFluidDependencies = Boolean.getBoolean(ASSUME_FLUID_DEPENDENCIES);
    }

    @Override
    public void maybeDiscardStateRequiredForGraphResolution() {
        if (!keepStateRequiredForGraphResolution) {
            dependencySubstitutions.discard();
        }
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
            parsedForcedModules = FORCED_MODULES_PARSER.parseNotation(forcedModules);
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
    public ResolutionStrategy failOnDynamicVersions() {
        mutationValidator.validateMutation(STRATEGY);
        this.failOnDynamicVersions = true;
        return this;
    }

    @Override
    public ResolutionStrategy failOnChangingVersions() {
        mutationValidator.validateMutation(STRATEGY);
        this.failOnChangingVersions = true;
        return this;
    }

    @Override
    public ResolutionStrategy failOnNonReproducibleResolution() {
        failOnChangingVersions();
        failOnDynamicVersions();
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
    public ResolutionStrategy deactivateDependencyLocking() {
        mutationValidator.validateMutation(STRATEGY);
        dependencyLockingEnabled = false;
        return this;
    }


    @Override
    public void sortArtifacts(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public ResolutionStrategy capabilitiesResolution(Action<? super CapabilitiesResolution> action) {
        action.execute(capabilitiesResolution);
        return this;
    }

    @Override
    public CapabilitiesResolution getCapabilitiesResolution() {
        return capabilitiesResolution;
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
    public ImmutableActionSet<DependencySubstitutionInternal> getDependencySubstitutionRule() {
        ImmutableActionSet<DependencySubstitutionInternal> result = ImmutableActionSet.empty();
        Set<ModuleVersionSelector> forcedModules = getForcedModules();
        if (!forcedModules.isEmpty()) {
            result = result.add(new ModuleForcingResolveRule(forcedModules));
        }
        result = result.add(dependencySubstitutions.getRuleAction());
        if (useGlobalDependencySubstitutionRules.get()) {
            result = result.add(globalDependencySubstitutionRules.getDependencySubstitutionRules().getRuleAction());
        }
        return result;
    }

    @Override
    public void assumeFluidDependencies() {
        assumeFluidDependencies = true;
    }

    @Override
    public boolean resolveGraphToDetermineTaskDependencies() {
        return assumeFluidDependencies
            || dependencySubstitutions.rulesMayAddProjectDependency()
            || (useGlobalDependencySubstitutionRules.get() && globalDependencySubstitutionRules.getDependencySubstitutionRules().rulesMayAddProjectDependency())
            || vcsResolver.hasRules();
    }

    @Override
    public DefaultResolutionStrategy setForcedModules(Object... moduleVersionSelectorNotations) {
        mutationValidator.validateMutation(STRATEGY);
        this.forcedModules.clear();
        force(moduleVersionSelectorNotations);
        return this;
    }

    @Override
    public CachePolicy getCachePolicy() {
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
    public Property<Boolean> getUseGlobalDependencySubstitutionRules() {
        return useGlobalDependencySubstitutionRules;
    }

    @Override
    public DefaultResolutionStrategy copy() {
        DefaultResolutionStrategy out = new DefaultResolutionStrategy(cachePolicy.copy(), dependencySubstitutions.copy(), globalDependencySubstitutionRules, vcsResolver, moduleIdentifierFactory, componentSelectorConverter, dependencyLockingProvider, capabilitiesResolution, objectFactory);

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
        if (isFailingOnDynamicVersions()) {
            out.failOnDynamicVersions();
        }
        if (isFailingOnChangingVersions()) {
            out.failOnChangingVersions();
        }
        if (!isDependencyVerificationEnabled()) {
            out.disableDependencyVerification();
        }
        out.getUseGlobalDependencySubstitutionRules().convention(useGlobalDependencySubstitutionRules.get());
        return out;
    }

    @Override
    public DependencyLockingProvider getDependencyLockingProvider() {
        if (dependencyLockingEnabled) {
            return dependencyLockingProvider;
        } else {
            throw new IllegalStateException("Dependency locking is not enabled");
        }
    }

    @Override
    public boolean isDependencyLockingEnabled() {
        return dependencyLockingEnabled;
    }

    @Override
    public CapabilitiesResolutionInternal getCapabilitiesResolutionRules() {
        return capabilitiesResolution;
    }

    @Override
    public boolean isFailingOnDynamicVersions() {
        return failOnDynamicVersions;
    }

    @Override
    public boolean isFailingOnChangingVersions() {
        return failOnChangingVersions;
    }

    @Override
    public boolean isDependencyVerificationEnabled() {
        return verifyDependencies;
    }

    @Override
    public ResolutionStrategy disableDependencyVerification() {
        verifyDependencies = false;
        return this;
    }

    @Override
    public ResolutionStrategy enableDependencyVerification() {
        verifyDependencies = true;
        return this;
    }

    @Override
    public void setIncludeAllSelectableVariantResults(boolean selectableVariantResults) {
        mutationValidator.validateMutation(STRATEGY);
        this.selectableVariantResults = selectableVariantResults;
    }

    @Override
    public boolean getIncludeAllSelectableVariantResults() {
        return this.selectableVariantResults;
    }

    @Override
    public void setKeepStateRequiredForGraphResolution(boolean keepStateRequiredForGraphResolution) {
        this.keepStateRequiredForGraphResolution = keepStateRequiredForGraphResolution;
    }
}
