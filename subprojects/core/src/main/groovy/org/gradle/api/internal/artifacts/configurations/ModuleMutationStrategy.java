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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.ModuleVersionSelector;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * by Szczepan Faber, created at: 11/29/12
 */
public class ModuleMutationStrategy {

    private Set<ModuleVersionSelector> forcedModules = new LinkedHashSet<ModuleVersionSelector>();
    private Action<? super DependencyResolveDetails> rule;

    public Set<ModuleVersionSelector> getForcedModules() {
        return forcedModules;
    }

    public Action<? super DependencyResolveDetails> getRule() {
        return rule;
    }

    public void addModules(Set<ModuleVersionSelector> forcedModules) {
        this.forcedModules.addAll(forcedModules);
    }

    public void setModules(Set<ModuleVersionSelector> forcedModules) {
        this.forcedModules = forcedModules;
    }

    public void eachDependency(Action<? super DependencyResolveDetails> rule) {
        this.rule = rule;
    }
}