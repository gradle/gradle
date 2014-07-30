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
import java.util.LinkedHashSet;
import java.util.Set;

class DefaultModuleConflict implements ModuleConflict {

    private final Collection<? extends ModuleRevisionResolveState> moduleVersions;
    private final Set<ModuleIdentifier> affectedModules = new LinkedHashSet<ModuleIdentifier>();

    public DefaultModuleConflict(Collection<? extends ModuleRevisionResolveState> moduleVersions) {
        this.moduleVersions = moduleVersions;
        //Collect modules participating in the conflict. Many times, it will be just a single module (that happen to have multiple candidate versions).
        for (ModuleRevisionResolveState m : moduleVersions) {
            affectedModules.add(m.getId().getModule());
        }
    }

    public Collection<? extends ModuleRevisionResolveState> getVersions() {
        return moduleVersions;
    }

    public void withAffectedModules(Action<ModuleIdentifier> affectedModulesAction) {
        for (ModuleIdentifier m : affectedModules) {
            affectedModulesAction.execute(m);
        }
    }
}
