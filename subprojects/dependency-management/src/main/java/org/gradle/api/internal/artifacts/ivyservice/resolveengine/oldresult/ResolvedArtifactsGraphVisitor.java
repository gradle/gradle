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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult;

import com.google.common.collect.Maps;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.component.model.ConfigurationMetaData;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.resolve.resolver.ArtifactResolver;

import java.util.Map;
import java.util.Set;

public class ResolvedArtifactsGraphVisitor implements DependencyGraphVisitor {
    private final IdGenerator<Long> idGenerator = new LongIdGenerator();
    private final Map<ResolvedConfigurationIdentifier, ArtifactSet> artifactSetsByConfiguration = Maps.newHashMap();
    private final Map<ComponentArtifactIdentifier, ResolvedArtifact> allResolvedArtifacts = Maps.newHashMap();
    private final ArtifactResolver artifactResolver;

    private final ResolvedArtifactsBuilder artifactResults;

    public ResolvedArtifactsGraphVisitor(ResolvedArtifactsBuilder artifactsBuilder, ArtifactResolver artifactResolver) {
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
            ResolvedConfigurationIdentifier parent = dependency.getFrom().getNodeId();
            ResolvedConfigurationIdentifier child = resolvedConfiguration.getNodeId();

            ArtifactSet artifacts = getArtifacts(dependency, resolvedConfiguration);
            artifactResults.addArtifacts(parent, child, artifacts);
        }
    }

    public void finish(DependencyGraphNode root) {
        allResolvedArtifacts.clear();
        artifactSetsByConfiguration.clear();
    }

    // TODO:DAZ This is functional, but need to refactor for clarity
    private ArtifactSet getArtifacts(DependencyGraphEdge dependency, DependencyGraphNode childConfiguration) {
        long id = idGenerator.generateId();
        ResolvedConfigurationIdentifier configurationIdentifier = childConfiguration.getNodeId();
        ConfigurationMetaData metaData = childConfiguration.getMetaData();
        ComponentResolveMetaData component = metaData.getComponent();

        Set<ComponentArtifactMetaData> artifacts = dependency.getArtifacts(metaData);
        if (!artifacts.isEmpty()) {
            return new DependencyArtifactSet(component.getId(), component.getSource(), artifacts, artifactResolver, allResolvedArtifacts, id);
        }

        ArtifactSet configurationArtifactSet = artifactSetsByConfiguration.get(configurationIdentifier);
        if (configurationArtifactSet == null) {

            configurationArtifactSet = new ConfigurationArtifactSet(component, configurationIdentifier, dependency.getSelector(), artifactResolver, allResolvedArtifacts, id);

            // Only share an ArtifactSet if the artifacts are not filtered by the dependency
            if (dependency.getSelector().acceptsAllArtifacts()) {
                artifactSetsByConfiguration.put(configurationIdentifier, configurationArtifactSet);
            }
        }

        return configurationArtifactSet;
    }
}
