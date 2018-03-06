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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.dsl.dependencies.CapabilityInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.ResolutionScopeCapabilitiesHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * A dependency graph visitor which validates that all modules in the graph which provide the same
 * capability are not in conflict. We have to do the validation late, because it's possible to
 * discover new capabilities, and more importantly preferences for capabilities, as the graph is
 * built (we don't know in advance what capabilities are going to be brought by transitive dependencies
 * and it's possible for a module to express a preference for a capability).
 */
public class CapabilitiesValidatingGraphVisitor implements DependencyGraphVisitor {
    private final DependencyGraphVisitor delegate;
    private final ResolutionScopeCapabilitiesHandler capabilitiesHandler;

    private final Map<ModuleIdentifier, Collection<? extends CapabilityInternal>> seenModulesToCapabilities = Maps.newHashMap();

    public CapabilitiesValidatingGraphVisitor(DependencyGraphVisitor delegate, ResolutionScopeCapabilitiesHandler capabilitiesHandler) {
        this.delegate = delegate;
        this.capabilitiesHandler = capabilitiesHandler;
    }

    @Override
    public void start(DependencyGraphNode root) {
        delegate.start(root);
    }

    @Override
    public void visitNode(DependencyGraphNode node) {
        delegate.visitNode(node);
        ModuleIdentifier module = node.getOwner().getModuleVersion().getModule();
        if (capabilitiesHandler.hasCapabilities()) {
            Collection<? extends CapabilityInternal> capabilities = capabilitiesHandler.getCapabilities(module);
            if (!capabilities.isEmpty()) {
                seenModulesToCapabilities.put(module, capabilities);
            }
        }
    }

    @Override
    public void visitSelector(DependencyGraphSelector selector) {
        delegate.visitSelector(selector);
    }

    @Override
    public void visitEdges(DependencyGraphNode node) {
        delegate.visitEdges(node);
    }

    @Override
    public void finish(DependencyGraphNode root) {
        delegate.finish(root);
        for (Map.Entry<ModuleIdentifier, Collection<? extends CapabilityInternal>> entry : seenModulesToCapabilities.entrySet()) {
            Collection<? extends CapabilityInternal> capabilities = entry.getValue();
            // for each capability of this module, we must check if there's at least one other module in the graph
            // which provides the same capability, in which case a preference should have been set
            Set<ModuleIdentifier> seen = Sets.newTreeSet(CapabilityInternal.MODULE_IDENTIFIER_COMPARATOR);
            for (CapabilityInternal capability : capabilities) {
                if (capability.getPrefer() == null) {
                    for (ModuleIdentifier provider : capability.getProvidedBy()) {
                        if (seenModulesToCapabilities.containsKey(provider)) {
                            seen.add(provider);
                        }
                    }
                }
                if (seen.size() > 1) {
                    // at least 2 modules provide the same capability, and they do not agree
                    throw new RuntimeException("Cannot choose between " + Joiner.on(" or ").join(seen) + " because they provide the same capability: " + capability.getCapabilityId());
                }
                seen.clear();
            }
        }
    }
}

