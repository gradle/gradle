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
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.cache.ResolutionRules;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.internal.Actions;
import org.gradle.api.internal.artifacts.DependencyResolveDetailsInternal;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.dsl.ModuleVersionSelectorParsers;
import org.gradle.internal.typeconversion.NormalizedTimeUnit;
import org.gradle.internal.typeconversion.TimeUnitsParser;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.gradle.util.GUtil.flattenElements;

public class DefaultResolutionStrategy implements ResolutionStrategyInternal {

    private Set<ModuleVersionSelector> forcedModules = new LinkedHashSet<ModuleVersionSelector>();
    private ConflictResolution conflictResolution = new LatestConflictResolution();
    private ComponentSelectionRulesInternal componentSelectionRules = new DefaultComponentSelectionRules();

    final Set<Action<? super DependencyResolveDetails>> dependencyResolveRules;
    private final DefaultCachePolicy cachePolicy;

    public DefaultResolutionStrategy() {
        this(new DefaultCachePolicy(), new LinkedHashSet<Action<? super DependencyResolveDetails>>());
    }

    DefaultResolutionStrategy(DefaultCachePolicy cachePolicy, Set<Action<? super DependencyResolveDetails>> dependencyResolveRules) {
        this.cachePolicy = cachePolicy;
        this.dependencyResolveRules = dependencyResolveRules;
    }

    public Set<ModuleVersionSelector> getForcedModules() {
        return forcedModules;
    }

    public ResolutionStrategy failOnVersionConflict() {
        this.conflictResolution = new StrictConflictResolution();
        return this;
    }

    public ConflictResolution getConflictResolution() {
        return this.conflictResolution;
    }

    public ResolutionRules getResolutionRules() {
        return cachePolicy;
    }

    public DefaultResolutionStrategy force(Object... moduleVersionSelectorNotations) {
        Set<ModuleVersionSelector> modules = ModuleVersionSelectorParsers.multiParser().parseNotation(moduleVersionSelectorNotations);
        this.forcedModules.addAll(modules);
        return this;
    }

    public ResolutionStrategy eachDependency(Action<? super DependencyResolveDetails> rule) {
        dependencyResolveRules.add(rule);
        return this;
    }

    public Action<DependencyResolveDetailsInternal> getDependencyResolveRule() {
        Collection allRules = flattenElements(new ModuleForcingResolveRule(forcedModules), dependencyResolveRules);
        return Actions.composite(allRules);
    }

    public DefaultResolutionStrategy setForcedModules(Object ... moduleVersionSelectorNotations) {
        Set<ModuleVersionSelector> forcedModules = ModuleVersionSelectorParsers.multiParser().parseNotation(moduleVersionSelectorNotations);
        this.forcedModules = forcedModules;
        return this;
    }

    public DefaultCachePolicy getCachePolicy() {
        return cachePolicy;
    }

    public void cacheDynamicVersionsFor(int value, String units) {
        NormalizedTimeUnit timeUnit = new TimeUnitsParser().parseNotation(units, value);
        cacheDynamicVersionsFor(timeUnit.getValue(), timeUnit.getTimeUnit());
    }

    public void cacheDynamicVersionsFor(int value, TimeUnit units) {
        this.cachePolicy.cacheDynamicVersionsFor(value, units);
    }

    public void cacheChangingModulesFor(int value, String units) {
        NormalizedTimeUnit timeUnit = new TimeUnitsParser().parseNotation(units, value);
        cacheChangingModulesFor(timeUnit.getValue(), timeUnit.getTimeUnit());
    }

    public void cacheChangingModulesFor(int value, TimeUnit units) {
        this.cachePolicy.cacheChangingModulesFor(value, units);
    }

    public ComponentSelectionRulesInternal getComponentSelection() {
        return componentSelectionRules;
    }

    public ResolutionStrategy componentSelection(Action<? super ComponentSelectionRules> action) {
        action.execute(componentSelectionRules);
        return this;
    }

    public DefaultResolutionStrategy copy() {
        DefaultResolutionStrategy out = new DefaultResolutionStrategy(cachePolicy.copy(),
                new LinkedHashSet<Action<? super DependencyResolveDetails>>(dependencyResolveRules));

        if (conflictResolution instanceof StrictConflictResolution) {
            out.failOnVersionConflict();
        }
        out.setForcedModules(getForcedModules());
        out.getComponentSelection().getRules().addAll(componentSelectionRules.getRules());
        return out;
    }
}