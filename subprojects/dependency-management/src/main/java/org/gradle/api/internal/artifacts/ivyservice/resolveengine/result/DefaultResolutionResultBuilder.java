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
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ComponentResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyResult;
import org.gradle.api.internal.artifacts.result.DefaultResolutionResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedComponentResult;
import org.gradle.internal.Factory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DefaultResolutionResultBuilder {
    private final Map<Long, DefaultResolvedComponentResult> modules = new HashMap<Long, DefaultResolvedComponentResult>();
    private final CachingDependencyResultFactory dependencyResultFactory = new CachingDependencyResultFactory();

    public static ResolutionResult empty(ModuleVersionIdentifier id, ComponentIdentifier componentIdentifier) {
        DefaultResolutionResultBuilder builder = new DefaultResolutionResultBuilder();
        builder.visitComponent(new DefaultComponentResult(0L, id, VersionSelectionReasons.ROOT, componentIdentifier));
        return builder.complete(0L);
    }

    public ResolutionResult complete(Long rootId) {
        return new DefaultResolutionResult(new RootFactory(modules.get(rootId)));
    }

    public void visitComponent(ComponentResult component) {
        create(component.getResultId(), component.getModuleVersion(), component.getSelectionReason(), component.getComponentId());
    }

    public void visitOutgoingEdges(Long fromComponent, Collection<? extends DependencyResult> dependencies) {
        for (DependencyResult d : dependencies) {
            DefaultResolvedComponentResult from = modules.get(fromComponent);
            org.gradle.api.artifacts.result.DependencyResult dependency;
            if (d.getFailure() != null) {
                dependency = dependencyResultFactory.createUnresolvedDependency(d.getRequested(), from, d.getReason(), d.getFailure());
            } else {
                DefaultResolvedComponentResult selected = modules.get(d.getSelected());
                dependency = dependencyResultFactory.createResolvedDependency(d.getRequested(), from, selected);
                selected.addDependent((ResolvedDependencyResult) dependency);
            }
            from.addDependency(dependency);
        }
    }

    private void create(Long id, ModuleVersionIdentifier moduleVersion, ComponentSelectionReason selectionReason, ComponentIdentifier componentId) {
        if (!modules.containsKey(id)) {
            modules.put(id, new DefaultResolvedComponentResult(moduleVersion, selectionReason, componentId));
        }
    }

    private static class RootFactory implements Factory<ResolvedComponentResult> {
        private DefaultResolvedComponentResult rootModule;

        public RootFactory(DefaultResolvedComponentResult rootModule) {
            this.rootModule = rootModule;
        }

        public ResolvedComponentResult create() {
            return rootModule;
        }
    }
}
