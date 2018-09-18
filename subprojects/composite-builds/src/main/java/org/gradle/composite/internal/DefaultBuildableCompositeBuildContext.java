/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.composite.internal;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.internal.Actions;
import org.gradle.internal.Pair;

import java.util.List;
import java.util.Set;

public class DefaultBuildableCompositeBuildContext implements CompositeBuildContext {
    // TODO: Synchronization
    private final Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> availableModules = Sets.newHashSet();
    private final List<Action<DependencySubstitution>> substitutionRules = Lists.newArrayList();

    public DefaultBuildableCompositeBuildContext() {
    }

    @Override
    public void addAvailableModules(Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> availableModules) {
        this.availableModules.addAll(availableModules);
    }

    @Override
    public void registerSubstitution(Action<DependencySubstitution> substitutions) {
        substitutionRules.add(substitutions);
    }

    @Override
    public Action<DependencySubstitution> getRuleAction() {
        List<Action<DependencySubstitution>> allActions = Lists.newArrayList();
        if (!availableModules.isEmpty()) {
            // Automatically substitute all available modules
            allActions.add(new CompositeBuildDependencySubstitutions(availableModules));
        }
        allActions.addAll(substitutionRules);
        return Actions.composite(allActions);
    }

    @Override
    public boolean hasRules() {
        return !(availableModules.isEmpty() && substitutionRules.isEmpty());
    }
}
