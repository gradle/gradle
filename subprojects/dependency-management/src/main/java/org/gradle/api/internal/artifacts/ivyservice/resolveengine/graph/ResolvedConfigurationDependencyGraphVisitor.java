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

import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.DefaultUnresolvedDependency;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.ResolvedConfigurationBuilder;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

class ResolvedConfigurationDependencyGraphVisitor implements DependencyGraphVisitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolvedConfigurationDependencyGraphVisitor.class);

    private final ResolvedConfigurationBuilder builder;
    private final ArtifactResolver artifactResolver;
    private final Map<ModuleVersionSelector, BrokenDependency> failuresByRevisionId = new LinkedHashMap<ModuleVersionSelector, BrokenDependency>();
    private DependencyGraphBuilder.ConfigurationNode root;

    ResolvedConfigurationDependencyGraphVisitor(ResolvedConfigurationBuilder builder, ArtifactResolver artifactResolver) {
        this.builder = builder;
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
            attachToParents(dependency, resolvedConfiguration, builder);
        }
    }

    private void attachToParents(DependencyGraphBuilder.DependencyEdge dependency, DependencyGraphBuilder.ConfigurationNode childConfiguration, ResolvedConfigurationBuilder oldModelBuilder) {
        ResolvedConfigurationIdentifier parent = dependency.from.id;
        ResolvedConfigurationIdentifier child = childConfiguration.id;
        oldModelBuilder.addChild(parent, child);
        oldModelBuilder.addParentSpecificArtifacts(child, parent, getArtifacts(dependency, childConfiguration, oldModelBuilder));

        if (parent == root.id) {
            ModuleDependency moduleDependency = dependency.getModuleDependency();
            oldModelBuilder.addFirstLevelDependency(moduleDependency, child);
        }
    }

    private Set<ResolvedArtifact> getArtifacts(DependencyGraphBuilder.DependencyEdge dependency, DependencyGraphBuilder.ConfigurationNode childConfiguration, ResolvedConfigurationBuilder builder) {
        Set<ComponentArtifactMetaData> dependencyArtifacts = dependency.getArtifacts(childConfiguration.metaData);
        if (dependencyArtifacts.isEmpty()) {
            return childConfiguration.getArtifacts(builder);
        }
        Set<ResolvedArtifact> artifacts = new LinkedHashSet<ResolvedArtifact>();
        for (ComponentArtifactMetaData artifact : dependencyArtifacts) {
            artifacts.add(builder.newArtifact(childConfiguration.id, childConfiguration.metaData.getComponent(), artifact, artifactResolver));
        }
        return artifacts;
    }

    public void finish(DependencyGraphBuilder.ConfigurationNode root) {
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
