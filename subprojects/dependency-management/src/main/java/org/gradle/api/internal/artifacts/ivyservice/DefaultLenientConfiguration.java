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

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.DependencyGraphNodeResult;
import org.gradle.api.internal.artifacts.ResolveArtifactsBuildOperationType;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride;
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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResultsLoader;
import org.gradle.api.internal.artifacts.transform.VariantSelector;
import org.gradle.api.internal.artifacts.transform.VariantSelectorFactory;
import org.gradle.api.internal.artifacts.verification.exceptions.DependencyVerificationException;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.DisplayName;
import org.gradle.internal.UncheckedException;
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

    private final ResolveContext resolveContext;
    private final Set<UnresolvedDependency> unresolvedDependencies;
    private final VisitedArtifactsResults artifactResults;
    private final VisitedFileDependencyResults fileDependencyResults;
    private final TransientConfigurationResultsLoader transientConfigurationResultsFactory;
    private final VariantSelectorFactory variantSelectorFactory;
    private final AttributeContainerInternal implicitAttributes;
    private final BuildOperationExecutor buildOperationExecutor;
    private final DependencyVerificationOverride dependencyVerificationOverride;
    private final WorkerLeaseService workerLeaseService;

    private final boolean selectFromAllVariants;

    // Selected for the configuration
    private SelectedArtifactResults artifactsForThisConfiguration;
    private DependencyVerificationException dependencyVerificationException;

    public DefaultLenientConfiguration(ResolveContext resolveContext, boolean selectFromAllVariants, Set<UnresolvedDependency> unresolvedDependencies, VisitedArtifactsResults artifactResults, VisitedFileDependencyResults fileDependencyResults, TransientConfigurationResultsLoader transientConfigurationResultsLoader, VariantSelectorFactory variantSelectorFactory, BuildOperationExecutor buildOperationExecutor, DependencyVerificationOverride dependencyVerificationOverride, WorkerLeaseService workerLeaseService) {
        this.resolveContext = resolveContext;
        this.implicitAttributes = resolveContext.getAttributes().asImmutable();
        this.selectFromAllVariants = selectFromAllVariants;
        this.unresolvedDependencies = unresolvedDependencies;
        this.artifactResults = artifactResults;
        this.fileDependencyResults = fileDependencyResults;
        this.transientConfigurationResultsFactory = transientConfigurationResultsLoader;
        this.variantSelectorFactory = variantSelectorFactory;
        this.buildOperationExecutor = buildOperationExecutor;
        this.dependencyVerificationOverride = dependencyVerificationOverride;
        this.workerLeaseService = workerLeaseService;
    }

    private SelectedArtifactResults getSelectedArtifacts() {
        if (artifactsForThisConfiguration == null) {
            artifactsForThisConfiguration = artifactResults.selectLenient(Specs.satisfyAll(), variantSelectorFactory.create(implicitAttributes, false, selectFromAllVariants, resolveContext.getDependenciesResolverFactory()));
        }
        return artifactsForThisConfiguration;
    }

    public SelectedArtifactSet select() {
        return select(Specs.satisfyAll(), implicitAttributes, Specs.satisfyAll(), false, selectFromAllVariants);
    }

    public SelectedArtifactSet select(final Spec<? super Dependency> dependencySpec) {
        return select(dependencySpec, implicitAttributes, Specs.satisfyAll(), false, selectFromAllVariants);
    }

    @Override
    public SelectedArtifactSet select(final Spec<? super Dependency> dependencySpec, final AttributeContainerInternal requestedAttributes, final Spec<? super ComponentIdentifier> componentSpec, boolean allowNoMatchingVariants, boolean selectFromAllVariants) {
        VariantSelector selector = variantSelectorFactory.create(requestedAttributes, allowNoMatchingVariants, selectFromAllVariants, resolveContext.getDependenciesResolverFactory());
        SelectedArtifactResults artifactResults = this.artifactResults.selectLenient(componentSpec, selector);

        return new SelectedArtifactSet() {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                for (UnresolvedDependency unresolvedDependency : unresolvedDependencies) {
                    context.visitFailure(unresolvedDependency.getProblem());
                }
                context.add(artifactResults.getArtifacts());
            }

            @Override
            public void visitArtifacts(ArtifactVisitor visitor, boolean continueOnSelectionFailure) {
                if (!unresolvedDependencies.isEmpty()) {
                    for (UnresolvedDependency unresolvedDependency : unresolvedDependencies) {
                        visitor.visitFailure(unresolvedDependency.getProblem());
                    }
                    if (!continueOnSelectionFailure) {
                        return;
                    }
                }
                // This may be called from an unmanaged thread, so temporarily enlist the current thread as a worker if it is not already so that it can visit the results
                // It would be better to instead to memoize the results on the first visit so that this is not required
                workerLeaseService.runAsUnmanagedWorkerThread(() -> visitArtifactsWithBuildOperation(dependencySpec, artifactResults, fileDependencyResults, visitor));
            }
        };
    }

    private ResolveException getFailure() {
        List<Throwable> failures = new ArrayList<>();
        for (UnresolvedDependency unresolvedDependency : unresolvedDependencies) {
            failures.add(unresolvedDependency.getProblem());
        }
        return new ResolveException(resolveContext.getDisplayName(), failures);
    }

    public boolean hasError() {
        return unresolvedDependencies.size() > 0;
    }

    @Override
    public Set<UnresolvedDependency> getUnresolvedModuleDependencies() {
        return unresolvedDependencies;
    }

    public void rethrowFailure() throws ResolveException {
        if (hasError()) {
            throw getFailure();
        }
    }

    private TransientConfigurationResults loadTransientGraphResults(SelectedArtifactResults artifactResults) {
        return transientConfigurationResultsFactory.create(artifactResults);
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
        TransientConfigurationResults graphResults = loadTransientGraphResults(getSelectedArtifacts());
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
        Deque<ResolvedDependency> workQueue = new LinkedList<>(loadTransientGraphResults(getSelectedArtifacts()).getRootNode().getPublicView().getChildren());
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
        return getFiles(Specs.satisfyAll());
    }

    /**
     * Recursive but excludes unsuccessfully resolved artifacts.
     */
    @Override
    public Set<File> getFiles(Spec<? super Dependency> dependencySpec) {
        LenientFilesAndArtifactResolveVisitor visitor = new LenientFilesAndArtifactResolveVisitor();
        visitArtifactsWithBuildOperation(dependencySpec, getSelectedArtifacts(), fileDependencyResults, visitor);
        return visitor.files;
    }

    @Override
    public Set<ResolvedArtifact> getArtifacts() {
        return getArtifacts(Specs.satisfyAll());
    }

    /**
     * Recursive but excludes unsuccessfully resolved artifacts.
     */
    @Override
    public Set<ResolvedArtifact> getArtifacts(Spec<? super Dependency> dependencySpec) {
        LenientArtifactCollectingVisitor visitor = new LenientArtifactCollectingVisitor();
        visitArtifactsWithBuildOperation(dependencySpec, getSelectedArtifacts(), fileDependencyResults, visitor);
        return visitor.artifacts;
    }

    private void visitArtifactsWithBuildOperation(final Spec<? super Dependency> dependencySpec, final SelectedArtifactResults artifactResults, final VisitedFileDependencyResults fileDependencyResults, final ArtifactVisitor visitor) {
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                visitArtifacts(dependencySpec, artifactResults, fileDependencyResults, visitor);
                // With input validation, we sometimes may suppress this exception and not see it on second time
                // Caching it takes care of this
                if (dependencyVerificationException != null) {
                    throw dependencyVerificationException;
                } else {
                    try {
                        dependencyVerificationOverride.artifactsAccessed(resolveContext.getDisplayName());
                    } catch (DependencyVerificationException e) {
                        dependencyVerificationException = e;
                        throw e;
                    }
                }
                context.setResult(RESULT);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                String displayName = "Resolve files of " + resolveContext.getIdentityPath();
                return BuildOperationDescriptor
                    .displayName(displayName)
                    .progressDisplayName(displayName)
                    // TODO: Can we update this to use the identity path?
                    .details(new ResolveArtifactsDetails(resolveContext.getProjectPath().toString()));
            }
        });
    }

    private static class ResolveArtifactsDetails implements ResolveArtifactsBuildOperationType.Details {

        private final String configuration;

        public ResolveArtifactsDetails(String configuration) {
            this.configuration = configuration;
        }

        @Override
        public String getConfigurationPath() {
            return configuration;
        }

    }

    /**
     * Recursive, includes unsuccessfully resolved artifacts
     *
     * @param dependencySpec dependency spec
     */
    private void visitArtifacts(Spec<? super Dependency> dependencySpec, SelectedArtifactResults artifactResults, VisitedFileDependencyResults fileDependencyResults, ArtifactVisitor visitor) {

        //this is not very nice might be good enough until we get rid of ResolvedConfiguration and friends
        //avoid traversing the graph causing the full ResolvedDependency graph to be loaded for the most typical scenario
        if (dependencySpec == Specs.SATISFIES_ALL) {
            ParallelResolveArtifactSet.wrap(artifactResults.getArtifacts(), buildOperationExecutor).visit(visitor);
            return;
        }

        List<ResolvedArtifactSet> artifactSets = new ArrayList<>();

        for (Map.Entry<FileCollectionDependency, Integer> entry : fileDependencyResults.getFirstLevelFiles().entrySet()) {
            if (dependencySpec.isSatisfiedBy(entry.getKey())) {
                artifactSets.add(artifactResults.getArtifactsWithId(entry.getValue()));
            }
        }

        CachingDirectedGraphWalker<DependencyGraphNodeResult, ResolvedArtifact> walker = new CachingDirectedGraphWalker<>(new ResolvedDependencyArtifactsGraph(artifactSets));
        for (DependencyGraphNodeResult node : getFirstLevelNodes(dependencySpec)) {
            walker.add(node);
        }
        walker.findValues();
        ParallelResolveArtifactSet.wrap(CompositeResolvedArtifactSet.of(artifactSets), buildOperationExecutor).visit(visitor);
    }

    public ResolveContext getResolveContext() {
        return resolveContext;
    }

    @Override
    public Set<ResolvedDependency> getFirstLevelModuleDependencies() {
        return getFirstLevelModuleDependencies(Specs.SATISFIES_ALL);
    }

    private static class LenientArtifactCollectingVisitor implements ArtifactVisitor {
        final Set<ResolvedArtifact> artifacts = Sets.newLinkedHashSet();
        final Set<File> files = Sets.newLinkedHashSet();

        @Override
        public void visitArtifact(DisplayName variantName, AttributeContainer variantAttributes, List<? extends Capability> capabilities, ResolvableArtifact artifact) {
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

        public ArtifactResolveException(String type, String path, String displayName, Iterable<Throwable> failures) {
            super(path, failures);
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
