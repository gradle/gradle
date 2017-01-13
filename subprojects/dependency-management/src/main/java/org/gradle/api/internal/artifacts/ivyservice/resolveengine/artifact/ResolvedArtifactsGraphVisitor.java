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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.VariantMetadata;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentArtifactsResolveResult;

import java.util.Map;
import java.util.Set;

/**
 * Adapts a {@link DependencyArtifactsVisitor} to a {@link DependencyGraphVisitor}. Calculates the artifacts contributed by each edge in the graph and forwards the results to the artifact visitor.
 */
public class ResolvedArtifactsGraphVisitor implements DependencyGraphVisitor {
    private final IdGenerator<Long> idGenerator = new LongIdGenerator();
    private final Map<Long, ArtifactSet> artifactSetsByConfiguration = Maps.newHashMap();
    private final Map<ComponentArtifactIdentifier, ResolvedArtifact> allResolvedArtifacts = Maps.newHashMap();
    private final ArtifactResolver artifactResolver;
    private final ImmutableAttributesFactory attributesFactory;
    private final DependencyArtifactsVisitor artifactResults;

    public ResolvedArtifactsGraphVisitor(DependencyArtifactsVisitor artifactsBuilder, ArtifactResolver artifactResolver, ImmutableAttributesFactory attributesFactory) {
        this.artifactResults = artifactsBuilder;
        this.artifactResolver = artifactResolver;
        this.attributesFactory = attributesFactory;
    }

    @Override
    public void start(DependencyGraphNode root) {
        artifactResults.startArtifacts(root);
    }

    @Override
    public void visitNode(DependencyGraphNode resolvedConfiguration) {
    }

    @Override
    public void visitSelector(DependencyGraphSelector selector) {
    }

    public void visitEdges(DependencyGraphNode resolvedConfiguration) {
        for (DependencyGraphEdge dependency : resolvedConfiguration.getIncomingEdges()) {
            DependencyGraphNode parent = dependency.getFrom();
            ArtifactSet artifacts = getArtifacts(dependency, resolvedConfiguration);
            artifactResults.visitArtifacts(parent, resolvedConfiguration, artifacts);
        }
    }

    public void finish(DependencyGraphNode root) {
        artifactResults.finishArtifacts();
        allResolvedArtifacts.clear();
        artifactSetsByConfiguration.clear();
    }

    private ArtifactSet getArtifacts(DependencyGraphEdge dependency, DependencyGraphNode toConfiguration) {
        long id = idGenerator.generateId();
        ConfigurationMetadata configuration = toConfiguration.getMetadata();
        ComponentResolveMetadata component = toConfiguration.getOwner().getMetadata();

        Set<? extends ComponentArtifactMetadata> artifacts = dependency.getArtifacts(configuration);
        if (!artifacts.isEmpty()) {
            Set<DefaultVariantMetadata> variants = ImmutableSet.of(new DefaultVariantMetadata(ImmutableAttributes.EMPTY, artifacts));
            return new DefaultArtifactSet(component.getComponentId(), component.getId(), component.getSource(), ModuleExclusions.excludeNone(), variants, artifactResolver, allResolvedArtifacts, id, attributesFactory);
        }

        ArtifactSet configurationArtifactSet = artifactSetsByConfiguration.get(toConfiguration.getNodeId());
        if (configurationArtifactSet == null) {
            Set<? extends VariantMetadata> variants = doResolve(component, configuration);

            configurationArtifactSet = new DefaultArtifactSet(component.getComponentId(), component.getId(), component.getSource(), dependency.getExclusions(), variants, artifactResolver, allResolvedArtifacts, id, attributesFactory);

            // Only share an ArtifactSet if the artifacts are not filtered by the dependency
            if (!dependency.getExclusions().mayExcludeArtifacts()) {
                artifactSetsByConfiguration.put(toConfiguration.getNodeId(), configurationArtifactSet);
            }
        }

        return configurationArtifactSet;
    }

    private Set<? extends VariantMetadata> doResolve(ComponentResolveMetadata component, ConfigurationMetadata configuration) {
        BuildableComponentArtifactsResolveResult result = new DefaultBuildableComponentArtifactsResolveResult();
        artifactResolver.resolveArtifacts(component, result);
        return result.getResult().getVariantsFor(configuration);
    }
}
