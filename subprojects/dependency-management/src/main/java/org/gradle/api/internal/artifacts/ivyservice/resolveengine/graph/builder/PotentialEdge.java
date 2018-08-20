/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.internal.component.model.ComponentResolveMetadata;

/**
 * This class wraps knowledge about a potential edge to a component. It's called potential,
 * because when the edge is created we don't know if the target component exists, and, since
 * the edge is created internally by the engine, we don't want to fail if the target component
 * doesn't exist. This means that the edge would effectively be added if, and only if, the
 * target component exists. Checking if it does exist is currently done by fetching metadata,
 * but we could have a cheaper strategy (HEAD request, ...).
 */
class PotentialEdge {
    final EdgeState edge;
    final ModuleVersionIdentifier toModuleVersionId;
    final ComponentResolveMetadata metadata;
    final ComponentState component;

    private PotentialEdge(EdgeState edge, ModuleVersionIdentifier toModuleVersionId, ComponentResolveMetadata metadata, ComponentState component) {
        this.edge = edge;
        this.toModuleVersionId = toModuleVersionId;
        this.metadata = metadata;
        this.component = component;
    }

    static PotentialEdge of(ResolveState resolveState, NodeState from, ModuleComponentIdentifier toComponent, ModuleComponentSelector toSelector, ComponentIdentifier owner) {
        DependencyState dependencyState = new DependencyState(new LenientPlatformDependencyMetadata(resolveState, from, toSelector, toComponent, owner), resolveState.getComponentSelectorConverter());
        EdgeState edge = new EdgeState(from, dependencyState, from.previousTraversalExclusions, resolveState);
        ModuleVersionIdentifier toModuleVersionId = DefaultModuleVersionIdentifier.newId(toSelector.getModuleIdentifier(), toSelector.getVersion());
        ComponentState version = resolveState.getModule(toSelector.getModuleIdentifier()).getVersion(toModuleVersionId, toComponent);
        SelectorState selector = edge.getSelector();
        version.selectedBy(selector);
        // We need to check if the target version exists. For this, we have to try to get metadata for the aligned version.
        // If it's there, it means we can align, otherwise, we must NOT add the edge, or resolution would fail
        ComponentResolveMetadata metadata = version.getMetadataWithoutRetryMissing();
        return new PotentialEdge(edge, toModuleVersionId, metadata, version);
    }
}
