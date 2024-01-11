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

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.DependencyGraphNodeResult;
import org.gradle.api.internal.artifacts.ResolveArtifactsBuildOperationType;
import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.CompositeResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.LocalDependencyFiles;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ParallelResolveArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactsResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedFileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResultsLoader;
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector;
import org.gradle.api.internal.artifacts.verification.exceptions.DependencyVerificationException;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.DisplayName;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.graph.CachingDirectedGraphWalker;
import org.gradle.internal.graph.DirectedGraphWithEdgeValues;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.work.WorkerLeaseService;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultLenientConfiguration implements LenientConfiguration, VisitedArtifactSet {

    private final static ResolveArtifactsBuildOperationType.Result RESULT = new ResolveArtifactsBuildOperationType.Result() {
    };

    private final ResolutionHost resolutionHost;
    private final ImmutableAttributes implicitAttributes;
    private final VisitedGraphResults graphResults;
    private final VisitedArtifactsResults artifactResults;
    private final VisitedFileDependencyResults fileDependencyResults;
    private final TransientConfigurationResultsLoader transientConfigurationResultsFactory;
    private final BuildOperationExecutor buildOperationExecutor;
    private final DependencyVerificationOverride dependencyVerificationOverride;
    private final WorkerLeaseService workerLeaseService;
    private final ArtifactVariantSelector artifactVariantSelector;
    private final ResolutionStrategy.SortOrder sortOrder;

    // Selected for the configuration
    private SelectedArtifactResults artifactsForThisConfiguration;
    private DependencyVerificationException dependencyVerificationException;

    public DefaultLenientConfiguration(
        ResolutionHost resolutionHost,
        ImmutableAttributes implicitAttributes,
        VisitedGraphResults graphResults,
        VisitedArtifactsResults artifactResults,
        VisitedFileDependencyResults fileDependencyResults,
        TransientConfigurationResultsLoader transientConfigurationResultsLoader,
        BuildOperationExecutor buildOperationExecutor,
        DependencyVerificationOverride dependencyVerificationOverride,
        WorkerLeaseService workerLeaseService,
        ArtifactVariantSelector artifactVariantSelector,
        ResolutionStrategy.SortOrder sortOrder
    ) {
        this.resolutionHost = resolutionHost;
        this.implicitAttributes = implicitAttributes;
        this.graphResults = graphResults;
        this.artifactResults = artifactResults;
        this.fileDependencyResults = fileDependencyResults;
        this.transientConfigurationResultsFactory = transientConfigurationResultsLoader;
        this.buildOperationExecutor = buildOperationExecutor;
        this.dependencyVerificationOverride = dependencyVerificationOverride;
        this.workerLeaseService = workerLeaseService;
        this.artifactVariantSelector = artifactVariantSelector;
        this.sortOrder = sortOrder;
    }

    private SelectedArtifactResults getLenientArtifacts() {
        if (artifactsForThisConfiguration == null) {
            artifactsForThisConfiguration = artifactResults.select(artifactVariantSelector, getImplicitSelectionSpec(), sortOrder, true);
        }
        return artifactsForThisConfiguration;
    }

    public SelectedArtifactSet select() {
        return select(Specs.satisfyAll(), getImplicitSelectionSpec());
    }

    public SelectedArtifactSet select(final Spec<? super Dependency> dependencySpec) {
        return select(dependencySpec, getImplicitSelectionSpec());
    }

    private ArtifactSelectionSpec getImplicitSelectionSpec() {
        return new ArtifactSelectionSpec(implicitAttributes, Specs.satisfyAll(), false, false);
    }

    @Override
    public SelectedArtifactSet select(final Spec<? super Dependency> dependencySpec, ArtifactSelectionSpec spec) {
        SelectedArtifactResults selectedArtifacts = this.artifactResults.select(artifactVariantSelector, spec, sortOrder, false);

        return new SelectedArtifactSet() {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                graphResults.visitFailures(context::visitFailure);
                context.add(selectedArtifacts.getArtifacts());
            }

            @Override
            public void visitArtifacts(ArtifactVisitor visitor, boolean continueOnSelectionFailure) {
                if (graphResults.hasAnyFailure()) {
                    graphResults.visitFailures(visitor::visitFailure);
                    if (!continueOnSelectionFailure) {
                        return;
                    }
                }
                // This may be called from an unmanaged thread, so temporarily enlist the current thread as a worker if it is not already so that it can visit the results
                // It would be better to instead to memoize the results on the first visit so that this is not required
                workerLeaseService.runAsUnmanagedWorkerThread(() -> {
                    if (dependencySpec == Specs.SATISFIES_ALL) {
                        visitArtifactsWithBuildOperation(visitor, selectedArtifacts.getArtifacts());
                    } else {
                        ResolvedArtifactSet filteredArtifacts = resolveFilteredArtifacts(dependencySpec, selectedArtifacts.getArtifactsById(), fileDependencyResults);
                        visitArtifactsWithBuildOperation(visitor, filteredArtifacts);
                    }
                });
            }
        };
    }

    public VisitedGraphResults getGraphResults() {
        return graphResults;
    }

    @Override
    public Set<UnresolvedDependency> getUnresolvedModuleDependencies() {
        return graphResults.getUnresolvedDependencies();
    }

    private TransientConfigurationResults loadTransientGraphResults() {
        return transientConfigurationResultsFactory.create(getLenientArtifacts().getArtifactsById());
    }

    @Override
    public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) {
        Set<ResolvedDependency> matches = new LinkedHashSet<>();
        for (DependencyGraphNodeResult node : getFirstLevelNodes(dependencySpec)) {
            matches.add(node.getPublicView());
        }
        return matches;
    }

    private Set<DependencyGraphNodeResult> getFirstLevelNodes(Spec<? super Dependency> dependencySpec) {
        Set<DependencyGraphNodeResult> matches = new LinkedHashSet<>();
        TransientConfigurationResults graphResults = loadTransientGraphResults();
        for (Map.Entry<Dependency, DependencyGraphNodeResult> entry : graphResults.getFirstLevelDependencies().entrySet()) {
            if (dependencySpec.isSatisfiedBy(entry.getKey())) {
                matches.add(entry.getValue());
            }
        }
        return matches;
    }

    @Override
    public Set<ResolvedDependency> getAllModuleDependencies() {
        Set<ResolvedDependency> resolvedElements = new LinkedHashSet<>();
        TransientConfigurationResults graphResults = loadTransientGraphResults();
        Deque<ResolvedDependency> workQueue = new LinkedList<>(graphResults.getRootNode().getPublicView().getChildren());
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
    public Set<File> getFiles() {
        LenientFilesAndArtifactResolveVisitor visitor = new LenientFilesAndArtifactResolveVisitor();
        visitArtifactsWithBuildOperation(visitor, getLenientArtifacts().getArtifacts());
        return visitor.files;
    }

    @Override
    public Set<File> getFiles(Spec<? super Dependency> dependencySpec) {
        LenientFilesAndArtifactResolveVisitor visitor = new LenientFilesAndArtifactResolveVisitor();
        ResolvedArtifactSet artifactSet = resolveFilteredArtifacts(dependencySpec, getLenientArtifacts().getArtifactsById(), fileDependencyResults);
        visitArtifactsWithBuildOperation(visitor, artifactSet);
        return visitor.files;
    }

    @Override
    public Set<ResolvedArtifact> getArtifacts() {
        LenientArtifactCollectingVisitor visitor = new LenientArtifactCollectingVisitor();
        visitArtifactsWithBuildOperation(visitor, getLenientArtifacts().getArtifacts());
        return visitor.artifacts;
    }

    @Override
    public Set<ResolvedArtifact> getArtifacts(Spec<? super Dependency> dependencySpec) {
        LenientArtifactCollectingVisitor visitor = new LenientArtifactCollectingVisitor();
        ResolvedArtifactSet artifactSet = resolveFilteredArtifacts(dependencySpec, getLenientArtifacts().getArtifactsById(), fileDependencyResults);
        visitArtifactsWithBuildOperation(visitor, artifactSet);
        return visitor.artifacts;
    }

    private void visitArtifactsWithBuildOperation(final ArtifactVisitor visitor, final ResolvedArtifactSet artifactSet) {
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                ParallelResolveArtifactSet.wrap(artifactSet, buildOperationExecutor).visit(visitor);

                // With input validation, we sometimes may suppress this exception and not see it on second time
                // Caching it takes care of this
                if (dependencyVerificationException != null) {
                    throw dependencyVerificationException;
                } else {
                    try {
                        dependencyVerificationOverride.artifactsAccessed(resolutionHost.getDisplayName());
                    } catch (DependencyVerificationException e) {
                        dependencyVerificationException = e;
                        throw e;
                    }
                }
                context.setResult(RESULT);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                String displayName = "Resolve files of " + resolutionHost.getDisplayName();
                return BuildOperationDescriptor
                    .displayName(displayName)
                    .progressDisplayName(displayName)
                    .details(new ResolveArtifactsDetails());
            }
        });
    }

    private static class ResolveArtifactsDetails implements ResolveArtifactsBuildOperationType.Details {

        @Override
        @Deprecated
        public String getConfigurationPath() {
            return "";
        }

    }

    /**
     * Recursive, includes unsuccessfully resolved artifacts
     *
     * @param dependencySpec dependency spec
     */
    private ResolvedArtifactSet resolveFilteredArtifacts(Spec<? super Dependency> dependencySpec, SelectedArtifactResults.ArtifactsById artifactResults, VisitedFileDependencyResults fileDependencyResults) {
        List<ResolvedArtifactSet> artifactSets = new ArrayList<>();

        for (Map.Entry<FileCollectionDependency, Integer> entry : fileDependencyResults.getFirstLevelFiles().entrySet()) {
            if (dependencySpec.isSatisfiedBy(entry.getKey())) {
                artifactSets.add(artifactResults.get(entry.getValue()));
            }
        }

        CachingDirectedGraphWalker<DependencyGraphNodeResult, ResolvedArtifact> walker = new CachingDirectedGraphWalker<>(new ResolvedDependencyArtifactsGraph(artifactSets));
        for (DependencyGraphNodeResult node : getFirstLevelNodes(dependencySpec)) {
            walker.add(node);
        }
        walker.findValues();
        return CompositeResolvedArtifactSet.of(artifactSets);
    }

    public String getDisplayName() {
        return resolutionHost.getDisplayName();
    }

    @Override
    public Set<ResolvedDependency> getFirstLevelModuleDependencies() {
        return getFirstLevelModuleDependencies(Specs.SATISFIES_ALL);
    }

    private static class LenientArtifactCollectingVisitor implements ArtifactVisitor {
        final Set<ResolvedArtifact> artifacts = new LinkedHashSet<>();
        final Set<File> files = new LinkedHashSet<>();

        @Override
        public void visitArtifact(DisplayName variantName, AttributeContainer variantAttributes, ImmutableCapabilities capabilities, ResolvableArtifact artifact) {
            try {
                ResolvedArtifact resolvedArtifact = artifact.toPublicView();
                files.add(resolvedArtifact.getFile());
                artifacts.add(resolvedArtifact);
            } catch (org.gradle.internal.resolve.ArtifactResolveException e) {
                //ignore
            }
        }

        @Override
        public FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
            if (source instanceof LocalDependencyFiles) {
                return FileCollectionStructureVisitor.VisitType.NoContents;
            }
            return FileCollectionStructureVisitor.VisitType.Visit;
        }

        @Override
        public boolean requireArtifactFiles() {
            return false;
        }

        @Override
        public void visitFailure(Throwable failure) {
            throw UncheckedException.throwAsUncheckedException(failure);
        }
    }

    private static class LenientFilesAndArtifactResolveVisitor extends LenientArtifactCollectingVisitor {
        @Override
        public FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
            return FileCollectionStructureVisitor.VisitType.Visit;
        }
    }

    public static class ArtifactResolveException extends ResolveException {
        private final String type;
        private final String displayName;

        public ArtifactResolveException(String type, String displayName, Iterable<? extends Throwable> failures) {
            super(displayName, failures);
            this.type = type;
            this.displayName = displayName;
        }

        // Need to override as error message is hardcoded in constructor of public type ResolveException
        @Override
        public String getMessage() {
            return String.format("Could not resolve all %s for %s.", type, displayName);
        }
    }

    private static class ResolvedDependencyArtifactsGraph implements DirectedGraphWithEdgeValues<DependencyGraphNodeResult, ResolvedArtifact> {
        private final List<ResolvedArtifactSet> dest;

        ResolvedDependencyArtifactsGraph(List<ResolvedArtifactSet> dest) {
            this.dest = dest;
        }

        @Override
        public void getNodeValues(DependencyGraphNodeResult node, Collection<? super ResolvedArtifact> values, Collection<? super DependencyGraphNodeResult> connectedNodes) {
            connectedNodes.addAll(node.getOutgoingEdges());
            dest.add(node.getArtifactsForNode());
        }

        @Override
        public void getEdgeValues(DependencyGraphNodeResult from, DependencyGraphNodeResult to, Collection<ResolvedArtifact> values) {
        }
    }
}
