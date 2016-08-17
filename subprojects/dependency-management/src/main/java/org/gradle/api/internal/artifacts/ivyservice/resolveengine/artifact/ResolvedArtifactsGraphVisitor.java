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

import com.google.common.collect.Maps;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
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
    private final Map<ResolvedConfigurationIdentifier, ArtifactSet> artifactSetsByConfiguration = Maps.newHashMap();
    private final Map<ComponentArtifactIdentifier, ResolvedArtifact> allResolvedArtifacts = Maps.newHashMap();
    private final ArtifactResolver artifactResolver;

    private final DependencyArtifactsVisitor artifactResults;

    public ResolvedArtifactsGraphVisitor(DependencyArtifactsVisitor artifactsBuilder, ArtifactResolver artifactResolver) {
        this.artifactResults = artifactsBuilder;
        this.artifactResolver = artifactResolver;
    }

    @Override
    public void start(DependencyGraphNode root) {
    }

    @Override
    public void visitNode(DependencyGraphNode resolvedConfiguration) {
    }

    public void visitEdge(DependencyGraphNode resolvedConfiguration) {
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

    private ArtifactSet getArtifacts(DependencyGraphEdge dependency, DependencyGraphNode childConfiguration) {
        long id = idGenerator.generateId();
        ResolvedConfigurationIdentifier configurationIdentifier = childConfiguration.getNodeId();
        ConfigurationMetadata configuration = childConfiguration.getMetadata();
        ComponentResolveMetadata component = childConfiguration.getOwner().getMetadata();

        Set<? extends ComponentArtifactMetadata> artifacts = dependency.getArtifacts(configuration);
        if (!artifacts.isEmpty()) {
            return new DefaultArtifactSet(component.getId(), component.getSource(), ModuleExclusions.excludeNone(), artifacts, artifactResolver, allResolvedArtifacts, id);
        }

        ArtifactSet configurationArtifactSet = artifactSetsByConfiguration.get(configurationIdentifier);
        if (configurationArtifactSet == null) {
            artifacts = doResolve(component, configuration);

            configurationArtifactSet = new DefaultArtifactSet(component.getId(), component.getSource(), dependency.getExclusions(), artifacts, artifactResolver, allResolvedArtifacts, id);

            // Only share an ArtifactSet if the artifacts are not filtered by the dependency
            if (!dependency.getExclusions().mayExcludeArtifacts()) {
                artifactSetsByConfiguration.put(configurationIdentifier, configurationArtifactSet);
            }
        }

        return configurationArtifactSet;
    }

    private Set<? extends ComponentArtifactMetadata> doResolve(ComponentResolveMetadata component, ConfigurationMetadata configuration) {
        BuildableComponentArtifactsResolveResult result = new DefaultBuildableComponentArtifactsResolveResult();
        artifactResolver.resolveArtifacts(component, result);
        return result.getResult().getArtifactsFor(configuration);
    }
}
