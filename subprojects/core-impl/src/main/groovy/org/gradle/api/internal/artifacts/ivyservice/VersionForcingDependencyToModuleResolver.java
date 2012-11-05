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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ModuleVersionSelector;

import java.util.HashMap;
import java.util.Map;

public class VersionForcingDependencyToModuleResolver implements DependencyToModuleVersionIdResolver {
    private final DependencyToModuleVersionIdResolver resolver;
    private final Map<ModuleId, ModuleRevisionId> forcedModules = new HashMap<ModuleId, ModuleRevisionId>();

    public VersionForcingDependencyToModuleResolver(DependencyToModuleVersionIdResolver resolver, Iterable<? extends ModuleVersionSelector> forcedModules) {
        this.resolver = resolver;
        for (ModuleVersionSelector forcedModule : forcedModules) {
            ModuleId moduleId = new ModuleId(forcedModule.getGroup(), forcedModule.getName());
            this.forcedModules.put(moduleId, new ModuleRevisionId(moduleId, forcedModule.getVersion()));
        }
    }

    public ModuleVersionIdResolveResult resolve(DependencyDescriptor dependencyDescriptor) {
        ModuleRevisionId newRevisionId = forcedModules.get(dependencyDescriptor.getDependencyId());
        if (newRevisionId != null) {
            ModuleVersionIdResolveResult result = resolver.resolve(dependencyDescriptor.clone(newRevisionId));
            return new ForcedModuleVersionIdResolveResult(result);
        }
        return resolver.resolve(dependencyDescriptor);
    }
}
