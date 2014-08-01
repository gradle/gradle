/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleRevisionResolveState;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;

class DefaultModuleConflict implements ModuleConflict {

    private final Collection<ModuleRevisionResolveState> moduleVersions = new LinkedHashSet<ModuleRevisionResolveState>();
    private final Collection<ModuleRevisionResolveState> replacedVersions = new LinkedHashSet<ModuleRevisionResolveState>();

    public DefaultModuleConflict(Collection<? extends ModuleRevisionResolveState> moduleVersions) {
        addVersions(moduleVersions);
    }

    public void addVersions(Collection<? extends ModuleRevisionResolveState> moduleVersions) {
        this.moduleVersions.addAll(moduleVersions);
    }

    public Collection<? extends ModuleRevisionResolveState> getVersions() {
        if (!replacedVersions.isEmpty()) {
            return replacedVersions;
        }
        return moduleVersions;
    }

    public void withAffectedModules(Action<ModuleIdentifier> affectedModulesAction) {
        //walk through all modules involved in the conflict. Many times, there will be only one involved module
        HashSet<ModuleIdentifier> visited = new HashSet<ModuleIdentifier>();
        withAffectedModules(moduleVersions, affectedModulesAction, visited);
        withAffectedModules(replacedVersions, affectedModulesAction, visited);
    }

    private static void withAffectedModules(Collection<ModuleRevisionResolveState> versions, Action<ModuleIdentifier> affectedModulesAction, HashSet<ModuleIdentifier> visited) {
        for (ModuleRevisionResolveState v : versions) {
            ModuleIdentifier module = v.getId().getModule();
            if (visited.add(module)) {
                affectedModulesAction.execute(module);
            }
        }
    }

    public void replacedBy(Collection<? extends ModuleRevisionResolveState> versions) {
        replacedVersions.addAll(versions);
    }
}