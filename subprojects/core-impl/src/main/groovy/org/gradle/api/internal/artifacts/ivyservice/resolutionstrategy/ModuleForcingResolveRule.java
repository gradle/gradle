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

package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DependencyResolveDetailsInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ModuleForcingResolveRule implements Action<DependencyResolveDetailsInternal> {

    private final Map<ModuleIdentifier, String> forcedModules;

    public ModuleForcingResolveRule(Collection<? extends ModuleVersionSelector> forcedModules) {
        if (!forcedModules.isEmpty()) {
            this.forcedModules = new HashMap<ModuleIdentifier, String>();
            for (ModuleVersionSelector module : forcedModules) {
                this.forcedModules.put(new DefaultModuleIdentifier(module.getGroup(), module.getName()), module.getVersion());
            }
        } else {
            this.forcedModules = null;
        }
    }

    public void execute(DependencyResolveDetailsInternal details) {
        if (forcedModules == null) {
            return;
        }
        ModuleIdentifier key = new DefaultModuleIdentifier(details.getRequested().getGroup(), details.getRequested().getName());
        if (forcedModules.containsKey(key)) {
            details.useVersion(forcedModules.get(key), VersionSelectionReasons.FORCED);
        }
    }
}