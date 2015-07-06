/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

import com.google.common.collect.Maps;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.DefaultUnresolvedDependency;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.ResolvedArtifactsBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.ResolvedConfigurationBuilder;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.component.model.ConfigurationMetaData;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

class ResolvedConfigurationDependencyGraphVisitor implements DependencyGraphVisitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolvedConfigurationDependencyGraphVisitor.class);

    private final IdGenerator<Long> idGenerator = new LongIdGenerator();
    private final ResolvedConfigurationBuilder builder;
    private final ResolvedArtifactsBuilder artifactsBuilder;
    private final ArtifactResolver artifactResolver;
    private final Map<ModuleVersionSelector, BrokenDependency> failuresByRevisionId = new LinkedHashMap<ModuleVersionSelector, BrokenDependency>();
    private final Map<ComponentArtifactIdentifier, ResolvedArtifact> allResolvedArtifacts = Maps.newHashMap();
    private final Map<ResolvedConfigurationIdentifier, ArtifactSet> artifactSetsByConfiguration = Maps.newHashMap();
    private DependencyGraphNode root;

    ResolvedConfigurationDependencyGraphVisitor(ResolvedConfigurationBuilder builder, ResolvedArtifactsBuilder artifactsBuilder, ArtifactResolver artifactResolver) {
        this.builder = builder;
        this.artifactsBuilder = artifactsBuilder;
        this.artifactResolver = artifactResolver;
    }

    public void start(DependencyGraphNode root) {
        this.root = root;
    }

    public void visitNode(DependencyGraphNode resolvedConfiguration) {
        builder.newResolvedDependency(resolvedConfiguration.getNodeId());
        for (DependencyGraphEdge dependency : resolvedConfiguration.getOutgoingEdges()) {
            ModuleVersionResolveException failure = dependency.getFailure();
            if (failure != null) {
                addUnresolvedDependency(dependency, dependency.getRequestedModuleVersion(), failure);
            }
        }
    }

    public void visitEdge(DependencyGraphNode resolvedConfiguration) {
        LOGGER.debug("Attaching {} to its parents.", resolvedConfiguration);
        for (DependencyGraphEdge dependency : resolvedConfiguration.getIncomingEdges()) {
            attachToParents(dependency, resolvedConfiguration);
        }
    }

    private void attachToParents(DependencyGraphEdge dependency, DependencyGraphNode childConfiguration) {
        ResolvedConfigurationIdentifier parent = dependency.getFrom().getNodeId();
        ResolvedConfigurationIdentifier child = childConfiguration.getNodeId();
        builder.addChild(parent, child);

        ArtifactSet artifacts = getArtifacts(dependency, childConfiguration);
        builder.addArtifacts(child, parent, artifacts.getId());
        artifactsBuilder.addArtifacts(artifacts.getId(), artifacts);

        if (parent == root.getNodeId()) {
            ModuleDependency moduleDependency = dependency.getModuleDependency();
            builder.addFirstLevelDependency(moduleDependency, child);
        }
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

    public void finish(DependencyGraphNode root) {
        allResolvedArtifacts.clear();
        artifactSetsByConfiguration.clear();
        attachFailures(builder);
        builder.done(root.getNodeId());
    }

    private void attachFailures(ResolvedConfigurationBuilder result) {
        for (Map.Entry<ModuleVersionSelector, BrokenDependency> entry : failuresByRevisionId.entrySet()) {
            Collection<List<ModuleVersionIdentifier>> paths = DependencyGraphPathResolver.calculatePaths(entry.getValue().requiredBy, root);
            result.addUnresolvedDependency(new DefaultUnresolvedDependency(entry.getKey(), entry.getValue().failure.withIncomingPaths(paths)));
        }
    }

    private void addUnresolvedDependency(DependencyGraphEdge dependency, ModuleVersionSelector requested, ModuleVersionResolveException failure) {
        BrokenDependency breakage = failuresByRevisionId.get(requested);
        if (breakage == null) {
            breakage = new BrokenDependency(failure);
            failuresByRevisionId.put(requested, breakage);
        }
        breakage.requiredBy.add(dependency.getFrom());
    }

    private static class BrokenDependency {
        final ModuleVersionResolveException failure;
        final List<DependencyGraphNode> requiredBy = new ArrayList<DependencyGraphNode>();

        private BrokenDependency(ModuleVersionResolveException failure) {
            this.failure = failure;
        }
    }

}
