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

package org.gradle.api.tasks.diagnostics.internal.graph.nodes;

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Children of this renderable dependency node are its dependents.
 */
public class InvertedRenderableModuleResult extends RenderableModuleResult {

    public InvertedRenderableModuleResult(ResolvedComponentResult module) {
        super(module);
    }

    @Override
    public Set<RenderableDependency> getChildren() {
        Map<ComponentIdentifier, RenderableDependency> children = new LinkedHashMap<ComponentIdentifier, RenderableDependency>();
        for (ResolvedDependencyResult dependent : module.getDependents()) {
            InvertedRenderableModuleResult child = new InvertedRenderableModuleResult(dependent.getFrom());
            if (!children.containsKey(child.getId())) {
                children.put(child.getId(), child);
            }
        }
        return new LinkedHashSet<RenderableDependency>(children.values());
    }

    @Override
    public String getName() {
        Set<? extends ResolvedDependencyResult> dependents = module.getDependents();
        Set<ComponentSelector> requestedWithSameModuleAsSelected = Sets.newLinkedHashSet();
        ModuleComponentIdentifier selected = null;
        for (ResolvedDependencyResult dependent : dependents) {
            ComponentSelector e = dependent.getRequested();
            ComponentIdentifier id = dependent.getSelected().getId();
            if (id instanceof ModuleComponentIdentifier) {
                if (e instanceof ModuleComponentSelector) {
                    ModuleComponentSelector mcs = (ModuleComponentSelector) e;
                    if (selected == null || mcs.getModuleIdentifier().equals(selected.getModuleIdentifier())) {
                        selected = (ModuleComponentIdentifier) id;
                        if (!mcs.getVersion().equals(selected.getVersion())) {
                            requestedWithSameModuleAsSelected.add(e);
                        }
                    }
                }
            }

        }
        if (selected != null && !requestedWithSameModuleAsSelected.isEmpty()) {
            ModuleIdentifier mid = selected.getModuleIdentifier();
            StringBuilder sb = new StringBuilder();
            sb.append(mid.getGroup()).append(":").append(mid.getName()).append(":");
            boolean comma = false;
            for (ComponentSelector componentSelector : requestedWithSameModuleAsSelected) {
                String version = ((ModuleComponentSelector) componentSelector).getVersion();
                if (!version.equals(selected.getVersion())) {
                    if (comma) {
                        sb.append(",");
                    }
                    comma = true;
                    sb.append(version);
                }
            }
            sb.append(" -> ").append(selected.getVersion());
            return sb.toString();
        }
        return super.getName();
    }

}
