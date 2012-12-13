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
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.DependencyResolveDetailsInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
* by Szczepan Faber, created at: 11/29/12
*/
public class ModuleForcingResolveAction implements Action<DependencyResolveDetailsInternal> {

    private final Map<String, String> forcedModules;

    public ModuleForcingResolveAction(Collection<? extends ModuleVersionSelector> forcedModules) {
        if (!forcedModules.isEmpty()) {
            this.forcedModules = new HashMap<String, String>();
            for (ModuleVersionSelector module : forcedModules) {
                this.forcedModules.put(key(module), module.getVersion());
            }
        } else {
            this.forcedModules = null;
        }
    }

    public void execute(DependencyResolveDetailsInternal details) {
        if (forcedModules == null) {
            return;
        }
        String key = key(details.getRequested());
        if (forcedModules.containsKey(key)) {
            details.forceVersion(forcedModules.get(key), VersionSelectionReasons.FORCED);
        }
    }

    private String key(ModuleVersionSelector module) {
        return module.getGroup() + ":" + module.getName();
    }
}