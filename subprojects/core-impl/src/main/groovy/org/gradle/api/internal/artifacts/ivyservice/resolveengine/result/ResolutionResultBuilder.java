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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ModuleVersionSelectionReason;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.result.DefaultResolutionResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedModuleVersionResult;
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedDependencyResult;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * by Szczepan Faber, created at: 7/26/12
 */
public class ResolutionResultBuilder implements ResolvedConfigurationListener {

    private DefaultResolvedModuleVersionResult rootModule;

    private Map<ModuleVersionIdentifier, DefaultResolvedModuleVersionResult> modules
            = new LinkedHashMap<ModuleVersionIdentifier, DefaultResolvedModuleVersionResult>();

    //TODO SF/AM Use ModuleVersionIdentifier instead of ResolvedConfigurationIdentifier, then we can get rid of the EmptyDependencyGraph
    //and create an empty using: new ResolutionResultBuilder().start(DefaultModuleVersionIdentifier.newId(module)).getResult()
    public void start(ResolvedConfigurationIdentifier root) {
        rootModule = getModule(root.getId(), VersionSelectionReasons.REQUESTED);
    }

    public void resolvedConfiguration(ResolvedConfigurationIdentifier id, Collection<InternalDependencyResult> dependencies) {
        DefaultResolvedModuleVersionResult from = getModule(id.getId(), VersionSelectionReasons.REQUESTED);

        for (InternalDependencyResult d : dependencies) {
            if (d.getFailure() != null) {
                from.addDependency(new DefaultUnresolvedDependencyResult(d.getRequested(), d.getFailure(), from));
            } else {
                DefaultResolvedModuleVersionResult selected = getModule(d.getSelected().getSelectedId(), d.getSelected().getSelectionReason());
                DefaultResolvedDependencyResult dependency = new DefaultResolvedDependencyResult(d.getRequested(), selected, from);
                from.addDependency(dependency);
                selected.addDependent(dependency);
            }
        }
    }

    private DefaultResolvedModuleVersionResult getModule(ModuleVersionIdentifier id, ModuleVersionSelectionReason selectionReason) {
        if (!modules.containsKey(id)) {
            modules.put(id, new DefaultResolvedModuleVersionResult(id, selectionReason));
        }
        return modules.get(id);
    }

    public DefaultResolutionResult getResult() {
        return new DefaultResolutionResult(rootModule);
    }
}
