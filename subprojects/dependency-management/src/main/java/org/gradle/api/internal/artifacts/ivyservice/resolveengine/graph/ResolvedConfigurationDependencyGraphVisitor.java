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
    private DependencyGraphBuilder.ConfigurationNode root;

    ResolvedConfigurationDependencyGraphVisitor(ResolvedConfigurationBuilder builder, ResolvedArtifactsBuilder artifactsBuilder, ArtifactResolver artifactResolver) {
        this.builder = builder;
        this.artifactsBuilder = artifactsBuilder;
        this.artifactResolver = artifactResolver;
    }

    public void start(DependencyGraphBuilder.ConfigurationNode root) {
        this.root = root;
    }

    public void visitNode(DependencyGraphBuilder.ConfigurationNode resolvedConfiguration) {
        builder.newResolvedDependency(resolvedConfiguration.id);
        for (DependencyGraphBuilder.DependencyEdge dependency : resolvedConfiguration.outgoingEdges) {
            ModuleVersionResolveException failure = dependency.getFailure();
            if (failure != null) {
                addUnresolvedDependency(dependency, dependency.getRequestedModuleVersion(), failure);
            }
        }
    }

    public void visitEdge(DependencyGraphBuilder.ConfigurationNode resolvedConfiguration) {
        LOGGER.debug("Attaching {} to its parents.", resolvedConfiguration);
        for (DependencyGraphBuilder.DependencyEdge dependency : resolvedConfiguration.incomingEdges) {
            attachToParents(dependency, resolvedConfiguration);
        }
    }

    private void attachToParents(DependencyGraphBuilder.DependencyEdge dependency, DependencyGraphBuilder.ConfigurationNode childConfiguration) {
        ResolvedConfigurationIdentifier parent = dependency.from.id;
        ResolvedConfigurationIdentifier child = childConfiguration.id;
        builder.addChild(parent, child);

        ArtifactSet artifacts = getArtifacts(dependency, childConfiguration);
        builder.addArtifacts(child, parent, artifacts.getId());
        artifactsBuilder.addArtifacts(artifacts.getId(), artifacts);

        if (parent == root.id) {
            ModuleDependency moduleDependency = dependency.getModuleDependency();
            builder.addFirstLevelDependency(moduleDependency, child);
        }
    }

    // TODO:DAZ This is functional, but need to refactor for clarity
    private ArtifactSet getArtifacts(DependencyGraphBuilder.DependencyEdge dependency, DependencyGraphBuilder.ConfigurationNode childConfiguration) {
        long id = idGenerator.generateId();
        ResolvedConfigurationIdentifier configurationIdentifier = childConfiguration.id;
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

    public void finish(DependencyGraphBuilder.ConfigurationNode root) {
        allResolvedArtifacts.clear();
        artifactSetsByConfiguration.clear();
        attachFailures(builder);
        builder.done(root.id);
    }

    private void attachFailures(ResolvedConfigurationBuilder result) {
        for (Map.Entry<ModuleVersionSelector, BrokenDependency> entry : failuresByRevisionId.entrySet()) {
            Collection<List<ModuleVersionIdentifier>> paths = calculatePaths(entry.getValue());
            result.addUnresolvedDependency(new DefaultUnresolvedDependency(entry.getKey(), entry.getValue().failure.withIncomingPaths(paths)));
        }
    }

    private Collection<List<ModuleVersionIdentifier>> calculatePaths(BrokenDependency brokenDependency) {
        // Include the shortest path from each version that has a direct dependency on the broken dependency, back to the root

        Map<DependencyGraphBuilder.ModuleVersionResolveState, List<ModuleVersionIdentifier>> shortestPaths = new LinkedHashMap<DependencyGraphBuilder.ModuleVersionResolveState, List<ModuleVersionIdentifier>>();
        List<ModuleVersionIdentifier> rootPath = new ArrayList<ModuleVersionIdentifier>();
        rootPath.add(root.toId());
        shortestPaths.put(root.moduleRevision, rootPath);

        Set<DependencyGraphBuilder.ModuleVersionResolveState> directDependees = new LinkedHashSet<DependencyGraphBuilder.ModuleVersionResolveState>();
        for (DependencyGraphBuilder.ConfigurationNode node : brokenDependency.requiredBy) {
            directDependees.add(node.moduleRevision);
        }

        Set<DependencyGraphBuilder.ModuleVersionResolveState> seen = new HashSet<DependencyGraphBuilder.ModuleVersionResolveState>();
        LinkedList<DependencyGraphBuilder.ModuleVersionResolveState> queue = new LinkedList<DependencyGraphBuilder.ModuleVersionResolveState>();
        queue.addAll(directDependees);
        while (!queue.isEmpty()) {
            DependencyGraphBuilder.ModuleVersionResolveState version = queue.getFirst();
            if (version == root.moduleRevision) {
                queue.removeFirst();
            } else if (seen.add(version)) {
                for (DependencyGraphBuilder.ModuleVersionResolveState incomingVersion : version.getIncoming()) {
                    queue.add(0, incomingVersion);
                }
            } else {
                queue.remove();
                List<ModuleVersionIdentifier> shortest = null;
                for (DependencyGraphBuilder.ModuleVersionResolveState incomingVersion : version.getIncoming()) {
                    List<ModuleVersionIdentifier> candidate = shortestPaths.get(incomingVersion);
                    if (candidate == null) {
                        continue;
                    }
                    if (shortest == null) {
                        shortest = candidate;
                    } else if (shortest.size() > candidate.size()) {
                        shortest = candidate;
                    }

                }
                if (shortest == null) {
                    continue;
                }
                List<ModuleVersionIdentifier> path = new ArrayList<ModuleVersionIdentifier>();
                path.addAll(shortest);
                path.add(version.id);
                shortestPaths.put(version, path);
            }
        }

        List<List<ModuleVersionIdentifier>> paths = new ArrayList<List<ModuleVersionIdentifier>>();
        for (DependencyGraphBuilder.ModuleVersionResolveState version : directDependees) {
            List<ModuleVersionIdentifier> path = shortestPaths.get(version);
            paths.add(path);
        }
        return paths;
    }

    private void addUnresolvedDependency(DependencyGraphBuilder.DependencyEdge dependency, ModuleVersionSelector requested, ModuleVersionResolveException failure) {
        BrokenDependency breakage = failuresByRevisionId.get(requested);
        if (breakage == null) {
            breakage = new BrokenDependency(failure);
            failuresByRevisionId.put(requested, breakage);
        }
        breakage.requiredBy.add(dependency.from);
    }

    private static class BrokenDependency {
        final ModuleVersionResolveException failure;
        final List<DependencyGraphBuilder.ConfigurationNode> requiredBy = new ArrayList<DependencyGraphBuilder.ConfigurationNode>();

        private BrokenDependency(ModuleVersionResolveException failure) {
            this.failure = failure;
        }
    }

}
