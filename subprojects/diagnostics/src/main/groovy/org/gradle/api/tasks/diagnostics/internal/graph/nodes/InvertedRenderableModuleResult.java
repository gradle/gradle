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

import org.gradle.api.artifacts.component.ComponentIdentifier;
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
}
