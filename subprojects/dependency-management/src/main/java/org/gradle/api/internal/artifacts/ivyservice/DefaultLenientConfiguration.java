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
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.failures.ResolutionFailure;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.failures.AbstractResolutionFailure;
import org.gradle.api.internal.artifacts.DependencyGraphNodeResult;
import org.gradle.api.internal.artifacts.ResolveArtifactsBuildOperationType;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BuildDependenciesVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.CompositeResolvedArtifactSet;
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
import org.gradle.api.internal.artifacts.transform.ArtifactTransforms;
import org.gradle.api.internal.artifacts.transform.VariantSelector;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.graph.CachingDirectedGraphWalker;
import org.gradle.internal.graph.DirectedGraphWithEdgeValues;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;

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
    private final ConfigurationInternal configuration;
    private final Set<UnresolvedDependency> unresolvedDependencies;
    private final VisitedArtifactsResults artifactResults;
    private final VisitedFileDependencyResults fileDependencyResults;
    private final TransientConfigurationResultsLoader transientConfigurationResultsFactory;
    private final ArtifactTransforms artifactTransforms;
    private final AttributeContainerInternal implicitAttributes;
    private final BuildOperationExecutor buildOperationExecutor;

    // Selected for the configuration
    private SelectedArtifactResults artifactsForThisConfiguration;

    public DefaultLenientConfiguration(ConfigurationInternal configuration, Set<UnresolvedDependency> unresolvedDependencies, VisitedArtifactsResults artifactResults, VisitedFileDependencyResults fileDependencyResults, TransientConfigurationResultsLoader transientConfigurationResultsLoader, ArtifactTransforms artifactTransforms, BuildOperationExecutor buildOperationExecutor) {
        this.configuration = configuration;
        this.implicitAttributes = configuration.getAttributes().asImmutable();
        this.unresolvedDependencies = unresolvedDependencies;
        this.artifactResults = artifactResults;
        this.fileDependencyResults = fileDependencyResults;
        this.transientConfigurationResultsFactory = transientConfigurationResultsLoader;
        this.artifactTransforms = artifactTransforms;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    private SelectedArtifactResults getSelectedArtifacts() {
        if (artifactsForThisConfiguration == null) {
            artifactsForThisConfiguration = artifactResults.select(Specs.<ComponentIdentifier>satisfyAll(), artifactTransforms.variantSelector(implicitAttributes, false));
        }
        return artifactsForThisConfiguration;
    }

    public SelectedArtifactSet select() {
        return select(Specs.<Dependency>satisfyAll(), implicitAttributes, Specs.<ComponentIdentifier>satisfyAll(), false);
    }

    public SelectedArtifactSet select(final Spec<? super Dependency> dependencySpec) {
        return select(dependencySpec, implicitAttributes, Specs.<ComponentIdentifier>satisfyAll(), false);
    }

    @Override
    public SelectedArtifactSet select(final Spec<? super Dependency> dependencySpec, final AttributeContainerInternal requestedAttributes, final Spec<? super ComponentIdentifier> componentSpec, boolean allowNoMatchingVariants) {
        final SelectedArtifactResults artifactResults;
        VariantSelector selector = artifactTransforms.variantSelector(requestedAttributes, allowNoMatchingVariants);
        artifactResults = this.artifactResults.select(componentSpec, selector);

        return new SelectedArtifactSet() {
            @Override
            public void collectBuildDependencies(BuildDependenciesVisitor visitor) {
                for (UnresolvedDependency unresolvedDependency : unresolvedDependencies) {
                    visitor.visitFailure(unresolvedDependency.getProblem());
                }
                artifactResults.getArtifacts().collectBuildDependencies(visitor);
            }

            @Override
            public void visitArtifacts(ArtifactVisitor visitor, boolean continueOnSelectionFailure) {
                if (!unresolvedDependencies.isEmpty()) {
                    for (final UnresolvedDependency unresolvedDependency : unresolvedDependencies) {
                        Throwable problem = unresolvedDependency.getProblem();
                        visitor.visitFailure(problem);
                        ModuleVersionSelector versionSelector = unresolvedDependency.getSelector();
                        visitor.visitResolutionFailure(AbstractResolutionFailure.of(DefaultModuleComponentIdentifier.newId(versionSelector.getGroup(), versionSelector.getName(), versionSelector.getVersion()), problem));
                    }
                    if (!continueOnSelectionFailure) {
                        return;
                    }
                }
                DefaultLenientConfiguration.this.visitArtifactsWithBuildOperation(dependencySpec, artifactResults, DefaultLenientConfiguration.this.fileDependencyResults, visitor);
            }
        };
    }

    private ResolveException getFailure() {
        List<Throwable> failures = new ArrayList<Throwable>();
        for (UnresolvedDependency unresolvedDependency : unresolvedDependencies) {
            failures.add(unresolvedDependency.getProblem());
        }
        return new ResolveException(configuration.getDisplayName(), failures);
    }

    public boolean hasError() {
        return unresolvedDependencies.size() > 0;
    }

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

    public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) {
        Set<ResolvedDependency> matches = new LinkedHashSet<ResolvedDependency>();
        for (DependencyGraphNodeResult node : getFirstLevelNodes(dependencySpec)) {
            matches.add(node.getPublicView());
        }
        return matches;
    }

    private Set<DependencyGraphNodeResult> getFirstLevelNodes(Spec<? super Dependency> dependencySpec) {
        Set<DependencyGraphNodeResult> matches = new LinkedHashSet<DependencyGraphNodeResult>();
        TransientConfigurationResults graphResults = loadTransientGraphResults(getSelectedArtifacts());
        for (Map.Entry<ModuleDependency, DependencyGraphNodeResult> entry : graphResults.getFirstLevelDependencies().entrySet()) {
            if (dependencySpec.isSatisfiedBy(entry.getKey())) {
                matches.add(entry.getValue());
            }
        }
        return matches;
    }

    public Set<ResolvedDependency> getAllModuleDependencies() {
        Set<ResolvedDependency> resolvedElements = new LinkedHashSet<ResolvedDependency>();
        Deque<ResolvedDependency> workQueue = new LinkedList<ResolvedDependency>();
        workQueue.addAll(loadTransientGraphResults(getSelectedArtifacts()).getRootNode().getPublicView().getChildren());
        while (!workQueue.isEmpty()) {
            ResolvedDependency item = workQueue.removeFirst();
            if (resolvedElements.add(item)) {
                final Set<ResolvedDependency> children = item.getChildren();
                if (children != null) {
                    workQueue.addAll(children);
                }
            }
        }
        return resolvedElements;
    }

    @Override
    public Set<File> getFiles() {
        return getFiles(Specs.<Dependency>satisfyAll());
    }

    /**
     * Recursive but excludes unsuccessfully resolved artifacts.
     */
    public Set<File> getFiles(Spec<? super Dependency> dependencySpec) {
        LenientFilesAndArtifactResolveVisitor visitor = new LenientFilesAndArtifactResolveVisitor();
        visitArtifactsWithBuildOperation(dependencySpec, getSelectedArtifacts(), fileDependencyResults, visitor);
        return visitor.files;
    }

    @Override
    public Set<ResolvedArtifact> getArtifacts() {
        return getArtifacts(Specs.<Dependency>satisfyAll());
    }

    /**
     * Recursive but excludes unsuccessfully resolved artifacts.
     */
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
                context.setResult(ResolveArtifactsBuildOperationType.RESULT);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                String displayName = "Resolve files of " + configuration.getIdentityPath();
                return BuildOperationDescriptor
                    .displayName(displayName)
                    .progressDisplayName(displayName)
                    .details(new ResolveArtifactsBuildOperationType.DetailsImpl(configuration.getPath()));
            }
        });
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

        List<ResolvedArtifactSet> artifactSets = new ArrayList<ResolvedArtifactSet>();

        if (visitor.includeFiles()) {
            for (Map.Entry<FileCollectionDependency, Integer> entry : fileDependencyResults.getFirstLevelFiles().entrySet()) {
                if (dependencySpec.isSatisfiedBy(entry.getKey())) {
                    artifactSets.add(artifactResults.getArtifactsWithId(entry.getValue()));
                }
            }
        }

        CachingDirectedGraphWalker<DependencyGraphNodeResult, ResolvedArtifact> walker = new CachingDirectedGraphWalker<DependencyGraphNodeResult, ResolvedArtifact>(new ResolvedDependencyArtifactsGraph(artifactSets));
        for (DependencyGraphNodeResult node : getFirstLevelNodes(dependencySpec)) {
            walker.add(node);
        }
        walker.findValues();
        ParallelResolveArtifactSet.wrap(CompositeResolvedArtifactSet.of(artifactSets), buildOperationExecutor).visit(visitor);
    }

    public ConfigurationInternal getConfiguration() {
        return configuration;
    }

    public Set<ResolvedDependency> getFirstLevelModuleDependencies() {
        return loadTransientGraphResults(getSelectedArtifacts()).getRootNode().getPublicView().getChildren();
    }

    private static class LenientArtifactCollectingVisitor implements ArtifactVisitor {
        final Set<ResolvedArtifact> artifacts = Sets.newLinkedHashSet();
        final Set<File> files = Sets.newLinkedHashSet();

        @Override
        public void visitArtifact(AttributeContainer variant, ResolvableArtifact artifact) {
            try {
                ResolvedArtifact resolvedArtifact = artifact.toPublicView();
                files.add(resolvedArtifact.getFile());
                artifacts.add(resolvedArtifact);
            } catch (org.gradle.internal.resolve.ArtifactResolveException e) {
                //ignore
            }
        }

        @Override
        public boolean includeFiles() {
            return false;
        }

        @Override
        public boolean requireArtifactFiles() {
            return false;
        }

        @Override
        public void visitFailure(Throwable failure) {
            throw UncheckedException.throwAsUncheckedException(failure);
        }

        @Override
        public void visitResolutionFailure(ResolutionFailure<?> resolutionFailure) {
        }

        @Override
        public void visitFile(ComponentArtifactIdentifier artifactIdentifier, AttributeContainer variant, File file) {
            throw new UnsupportedOperationException();
        }
    }

    private static class LenientFilesAndArtifactResolveVisitor extends LenientArtifactCollectingVisitor {
        @Override
        public boolean includeFiles() {
            return true;
        }

        @Override
        public void visitFile(ComponentArtifactIdentifier artifactIdentifier, AttributeContainer variant, File file) {
            files.add(file);
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
