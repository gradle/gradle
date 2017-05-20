/*
 * Copyright 2015 the original author or authors.
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentArtifactsResolveResult;

import java.util.Map;
import java.util.Set;

/**
 * Adapts a {@link DependencyArtifactsVisitor} to a {@link DependencyGraphVisitor}. Calculates the artifacts contributed by each edge in the graph and forwards the results to the artifact visitor.
 */
public class ResolvedArtifactsGraphVisitor implements DependencyGraphVisitor {
    private int nextId;
    private final Map<Long, ArtifactsForNode> artifactsByNodeId = Maps.newHashMap();
    private final Map<ComponentArtifactIdentifier, ResolvedArtifact> allResolvedArtifacts = Maps.newHashMap();
    private final ArtifactResolver artifactResolver;
    private final ArtifactTypeRegistry artifactTypeRegistry;
    private final DependencyArtifactsVisitor artifactResults;
    private final ModuleExclusions moduleExclusions;

    public ResolvedArtifactsGraphVisitor(DependencyArtifactsVisitor artifactsBuilder, ArtifactResolver artifactResolver, ArtifactTypeRegistry artifactTypeRegistry,  ModuleExclusions moduleExclusions) {
        this.artifactResults = artifactsBuilder;
        this.artifactResolver = artifactResolver;
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.moduleExclusions = moduleExclusions;
    }

    @Override
    public void start(DependencyGraphNode root) {
        artifactResults.startArtifacts(root);
    }

    @Override
    public void visitNode(DependencyGraphNode node) {
        artifactResults.visitNode(node);
    }

    @Override
    public void visitSelector(DependencyGraphSelector selector) {
    }

    @Override
    public void visitEdges(DependencyGraphNode node) {
        for (DependencyGraphEdge dependency : node.getIncomingEdges()) {
            DependencyGraphNode parent = dependency.getFrom();
            ArtifactsForNode artifacts = getArtifacts(dependency, node);
            artifactResults.visitArtifacts(parent, node, artifacts.artifactSetId, artifacts.artifactSet);
        }
        for (LocalFileDependencyMetadata fileDependency : node.getOutgoingFileEdges()) {
            int id = nextId++;
            artifactResults.visitArtifacts(node, fileDependency, id, new FileDependencyArtifactSet(fileDependency, artifactTypeRegistry));
        }
    }

    @Override
    public void finish(DependencyGraphNode root) {
        artifactResults.finishArtifacts();
        allResolvedArtifacts.clear();
        artifactsByNodeId.clear();
    }

    private ArtifactsForNode getArtifacts(DependencyGraphEdge dependency, DependencyGraphNode toConfiguration) {
        ConfigurationMetadata configuration = toConfiguration.getMetadata();
        ComponentResolveMetadata component = toConfiguration.getOwner().getMetadata();

        Set<? extends ComponentArtifactMetadata> artifacts = dependency.getArtifacts(configuration);
        if (!artifacts.isEmpty()) {
            Set<DefaultVariantMetadata> variants = ImmutableSet.of(new DefaultVariantMetadata(Describables.of(component.getComponentId()), ImmutableAttributes.EMPTY, artifacts));
            int id = nextId++;
            return new ArtifactsForNode(id, new DefaultArtifactSet(component.getComponentId(), component.getId(), component.getSource(), ModuleExclusions.excludeNone(), variants, component.getAttributesSchema(), artifactResolver, allResolvedArtifacts, artifactTypeRegistry));
        }

        ArtifactsForNode configurationArtifactSet = artifactsByNodeId.get(toConfiguration.getNodeId());
        if (configurationArtifactSet == null) {
            ModuleExclusion exclusions = dependency.getExclusions(moduleExclusions);
            ArtifactSet nodeArtifacts = doResolve(component, configuration, exclusions);
            int id = nextId++;
            configurationArtifactSet = new ArtifactsForNode(id, nodeArtifacts);

            // Only share an ArtifactSet if the artifacts are not filtered by the dependency
            if (!exclusions.mayExcludeArtifacts()) {
                artifactsByNodeId.put(toConfiguration.getNodeId(), configurationArtifactSet);
            }
        }

        return configurationArtifactSet;
    }

    private ArtifactSet doResolve(ComponentResolveMetadata component, ConfigurationMetadata configuration, ModuleExclusion exclusions) {
        BuildableComponentArtifactsResolveResult result = new DefaultBuildableComponentArtifactsResolveResult();
        artifactResolver.resolveArtifacts(component, result);
        return result.getResult().getArtifactsFor(component, configuration, artifactResolver, allResolvedArtifacts, artifactTypeRegistry, exclusions);
    }

    private static class ArtifactsForNode {
        private final int artifactSetId;
        private final ArtifactSet artifactSet;

        ArtifactsForNode(int artifactSetId, ArtifactSet artifactSet) {
            this.artifactSetId = artifactSetId;
            this.artifactSet = artifactSet;
        }
    }
}
