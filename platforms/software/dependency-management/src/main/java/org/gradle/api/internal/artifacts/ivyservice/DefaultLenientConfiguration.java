/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.internal.artifacts.DefaultResolvedDependency;
import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSetResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resolve.ArtifactResolveException;
import org.jspecify.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class DefaultLenientConfiguration implements LenientConfigurationInternal {

    private final ResolutionHost resolutionHost;
    private final VisitedGraphResults graphResults;
    private final VisitedArtifactSet artifactResults;
    private final Supplier<GraphStructure> graphStructureSupplier;
    private final ResolvedArtifactSetResolver artifactSetResolver;
    private final ArtifactSelectionSpec implicitSelectionSpec;
    private final BuildOperationExecutor buildOperationExecutor;

    // Selected for the configuration
    private @Nullable SelectedArtifactResults artifactsForThisConfiguration;

    /**
     * The resolved dependency graph is a view over the underlying GraphStructure and
     * provides no additional context. Only hold a soft reference to it to avoid retained
     * memory if no other references to the view graph exist.
     */
    private final Lock rootLock = new ReentrantLock();
    private @Nullable SoftReference<DefaultResolvedDependency> root = null;

    public DefaultLenientConfiguration(
        ResolutionHost resolutionHost,
        VisitedGraphResults graphResults,
        VisitedArtifactSet artifactResults,
        Supplier<GraphStructure> graphStructureSupplier,
        ResolvedArtifactSetResolver artifactSetResolver,
        ArtifactSelectionSpec implicitSelectionSpec,
        BuildOperationExecutor buildOperationExecutor
    ) {
        this.resolutionHost = resolutionHost;
        this.graphResults = graphResults;
        this.artifactResults = artifactResults;
        this.graphStructureSupplier = graphStructureSupplier;
        this.artifactSetResolver = artifactSetResolver;
        this.implicitSelectionSpec = implicitSelectionSpec;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    private SelectedArtifactResults getSelectedArtifacts() {
        if (artifactsForThisConfiguration == null) {
            artifactsForThisConfiguration = artifactResults.selectLegacy(implicitSelectionSpec);
        }
        return artifactsForThisConfiguration;
    }

    @Override
    public ArtifactSelectionSpec getImplicitSelectionSpec() {
        return implicitSelectionSpec;
    }

    @Override
    public Set<UnresolvedDependency> getUnresolvedModuleDependencies() {
        return graphResults.getUnresolvedDependencies();
    }

    private DefaultResolvedDependency getRoot() {
        rootLock.lock();
        try {
            if (root != null) {
                DefaultResolvedDependency value = root.get();
                if (value != null) {
                    return value;
                }
            }

            DefaultResolvedDependency value = buildRoot();
            this.root = new SoftReference<>(value);
            return value;
        } finally {
            rootLock.unlock();
        }
    }

    private DefaultResolvedDependency buildRoot() {
        GraphStructure structure = graphStructureSupplier.get();
        GraphStructure.Nodes nodes = structure.nodes();
        GraphStructure.Components components = structure.components();
        GraphStructure.Edges edges = structure.edges();
        SelectedArtifactResults artifactsByNodeId = getSelectedArtifacts();

        List<DefaultResolvedDependency> allNodes = new ArrayList<>(nodes.count());
        for (int i = 0; i < nodes.count(); i++) {
            int owner = nodes.owner(i);
            ResolvedArtifactSet artifacts = artifactsByNodeId.getArtifactsWithId(i);
            DefaultResolvedDependency node = new DefaultResolvedDependency(
                nodes.variantName(i),
                components.moduleVersionId(owner),
                buildOperationExecutor,
                resolutionHost
            );
            node.addModuleArtifacts(artifacts);
            allNodes.add(node);
        }

        for (int i = 0; i < nodes.count(); i++) {
            DefaultResolvedDependency parent = allNodes.get(i);
            for (int e = edges.start(i); e < edges.end(i); e++) {
                if (!edges.constraint(e)) {
                    int target = edges.targetNode(e);
                    if (target != -1) {
                        // Resolved/LenientConfiguration only expose
                        // successful, non-constraint edges.
                        parent.addChild(allNodes.get(target));
                    }
                }
            }
        }

        return allNodes.get(nodes.root());
    }

    @Override
    public ImmutableSet<ResolvedDependency> getFirstLevelModuleDependencies() {
        return getRoot().getChildren();
    }

    @Override
    public Set<ResolvedDependency> getAllModuleDependencies() {
        Set<ResolvedDependency> resolvedElements = new LinkedHashSet<>();
        Deque<ResolvedDependency> workQueue = new LinkedList<>(getRoot().getChildren());
        while (!workQueue.isEmpty()) {
            ResolvedDependency item = workQueue.removeFirst();
            if (resolvedElements.add(item)) {
                final Set<ResolvedDependency> children = item.getChildren();
                workQueue.addAll(children);
            }
        }
        return resolvedElements;
    }

    @Override
    public Set<ResolvedArtifact> getArtifacts() {
        ArtifactCollectingVisitor visitor = new ArtifactCollectingVisitor();
        artifactSetResolver.visitArtifacts(getSelectedArtifacts().getArtifacts(), visitor, resolutionHost);
        List<Throwable> allFailures = visitor.getFailures();
        if (!allFailures.isEmpty()) {
            Collection<Throwable> lenientFailures = allFailures.stream()
                // Ignore artifacts that cannot be resolved. Unexpected non-artifact
                // failures should still be elevated to the user.
                .filter(failure -> !(failure instanceof ArtifactResolveException))
                .toList();
            if (!lenientFailures.isEmpty()) {
                resolutionHost.rethrowFailuresAndReportProblems("artifacts", lenientFailures);
            }
        }
        return visitor.getArtifacts();
    }

}
