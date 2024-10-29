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
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.DependencyGraphNodeResult;
import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.CompositeResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultSelectedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.LocalDependencyFiles;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSetResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedFileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResultsLoader;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.graph.CachingDirectedGraphWalker;
import org.gradle.internal.graph.DirectedGraphWithEdgeValues;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultLenientConfiguration implements LenientConfigurationInternal {

    private final ResolutionHost resolutionHost;
    private final VisitedGraphResults graphResults;
    private final VisitedArtifactSet artifactResults;
    private final VisitedFileDependencyResults fileDependencyResults;
    private final TransientConfigurationResultsLoader transientConfigurationResultsFactory;
    private final ResolvedArtifactSetResolver artifactSetResolver;
    private final ArtifactSelectionSpec implicitSelectionSpec;

    // Selected for the configuration
    private SelectedArtifactResults artifactsForThisConfiguration;

    public DefaultLenientConfiguration(
        ResolutionHost resolutionHost,
        VisitedGraphResults graphResults,
        VisitedArtifactSet artifactResults,
        VisitedFileDependencyResults fileDependencyResults,
        TransientConfigurationResultsLoader transientConfigurationResultsLoader,
        ResolvedArtifactSetResolver artifactSetResolver,
        ArtifactSelectionSpec implicitSelectionSpec
    ) {
        this.resolutionHost = resolutionHost;
        this.graphResults = graphResults;
        this.artifactResults = artifactResults;
        this.fileDependencyResults = fileDependencyResults;
        this.transientConfigurationResultsFactory = transientConfigurationResultsLoader;
        this.artifactSetResolver = artifactSetResolver;
        this.implicitSelectionSpec = implicitSelectionSpec;
    }

    private SelectedArtifactResults getSelectedArtifacts() {
        if (artifactsForThisConfiguration == null) {
            artifactsForThisConfiguration = artifactResults.selectLegacy(implicitSelectionSpec, true);
        }
        return artifactsForThisConfiguration;
    }

    @Override
    public SelectedArtifactSet select(final Spec<? super Dependency> dependencySpec) {
        SelectedArtifactResults artifactResults = this.artifactResults.selectLegacy(implicitSelectionSpec, false);

        return new SelectedArtifactSet() {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                getDelegate(artifactResults.getArtifacts()).visitDependencies(context);
            }

            @Override
            public void visitArtifacts(ArtifactVisitor visitor, boolean continueOnSelectionFailure) {
                ResolvedArtifactSet filteredArtifacts = resolveFilteredArtifacts(dependencySpec, artifactResults);
                getDelegate(filteredArtifacts).visitArtifacts(visitor, continueOnSelectionFailure);
            }

            private DefaultSelectedArtifactSet getDelegate(ResolvedArtifactSet filteredArtifacts) {
                return new DefaultSelectedArtifactSet(artifactSetResolver, graphResults, filteredArtifacts, resolutionHost);
            }
        };
    }

    @Override
    public ArtifactSelectionSpec getImplicitSelectionSpec() {
        return implicitSelectionSpec;
    }

    @Override
    public Set<UnresolvedDependency> getUnresolvedModuleDependencies() {
        return graphResults.getUnresolvedDependencies();
    }

    private TransientConfigurationResults loadTransientGraphResults(SelectedArtifactResults artifactResults) {
        return transientConfigurationResultsFactory.create(artifactResults);
    }

    @Override
    public Set<ResolvedDependency> getFirstLevelModuleDependencies() {
        Set<ResolvedDependency> matches = new LinkedHashSet<>();
        for (DependencyGraphNodeResult node : loadTransientGraphResults(getSelectedArtifacts()).getFirstLevelDependencies().values()) {
            matches.add(node.getPublicView());
        }
        return matches;
    }

    @Override
    @Deprecated
    public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) {
        DeprecationLogger.deprecateMethod(LenientConfiguration.class, "getFirstLevelModuleDependencies(Spec)")
            .withAdvice("Use getFirstLevelModuleDependencies() instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecate_filtered_configuration_file_and_filecollection_methods")
            .nagUser();

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
    @Deprecated
    public Set<File> getFiles() {
        DeprecationLogger.deprecateMethod(LenientConfiguration.class, "getFiles()")
            .withAdvice("Use a lenient ArtifactView instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecate_legacy_configuration_get_files")
            .nagUser();

        LenientFilesAndArtifactResolveVisitor visitor = new LenientFilesAndArtifactResolveVisitor();
        artifactSetResolver.visitArtifacts(getSelectedArtifacts().getArtifacts(), visitor, resolutionHost);
        resolutionHost.rethrowFailuresAndReportProblems("files", visitor.getFailures());
        return visitor.files;
    }

    @Override
    @Deprecated
    public Set<File> getFiles(Spec<? super Dependency> dependencySpec) {
        DeprecationLogger.deprecateMethod(LenientConfiguration.class, "getFiles(Spec)")
            .withAdvice("Use a lenient ArtifactView with a componentFilter instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecate_filtered_configuration_file_and_filecollection_methods")
            .nagUser();

        LenientFilesAndArtifactResolveVisitor visitor = new LenientFilesAndArtifactResolveVisitor();
        ResolvedArtifactSet filteredArtifacts = resolveFilteredArtifacts(dependencySpec, getSelectedArtifacts());
        artifactSetResolver.visitArtifacts(filteredArtifacts, visitor, resolutionHost);
        resolutionHost.rethrowFailuresAndReportProblems("files", visitor.getFailures());
        return visitor.files;
    }

    @Override
    public Set<ResolvedArtifact> getArtifacts() {
        LenientArtifactCollectingVisitor visitor = new LenientArtifactCollectingVisitor();
        artifactSetResolver.visitArtifacts(getSelectedArtifacts().getArtifacts(), visitor, resolutionHost);
        resolutionHost.rethrowFailuresAndReportProblems("artifacts", visitor.getFailures());
        return visitor.artifacts;
    }

    @Override
    @Deprecated
    public Set<ResolvedArtifact> getArtifacts(Spec<? super Dependency> dependencySpec) {
        DeprecationLogger.deprecateMethod(LenientConfiguration.class, "getArtifacts(Spec)")
            .withAdvice("Use a lenient ArtifactView with a componentFilter instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecate_filtered_configuration_file_and_filecollection_methods")
            .nagUser();

        LenientArtifactCollectingVisitor visitor = new LenientArtifactCollectingVisitor();
        ResolvedArtifactSet filteredArtifacts = resolveFilteredArtifacts(dependencySpec, getSelectedArtifacts());
        artifactSetResolver.visitArtifacts(filteredArtifacts, visitor, resolutionHost);
        resolutionHost.rethrowFailuresAndReportProblems("artifacts", visitor.getFailures());
        return visitor.artifacts;
    }

    /**
     * Returns a subset of {@code artifactResults} accessible from dependencies matching {@code dependencySpec}.
     */
    private ResolvedArtifactSet resolveFilteredArtifacts(Spec<? super Dependency> dependencySpec, SelectedArtifactResults artifactResults) {
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
        return CompositeResolvedArtifactSet.of(artifactSets);
    }

    private static class LenientArtifactCollectingVisitor implements ArtifactVisitor {
        final Set<ResolvedArtifact> artifacts = new LinkedHashSet<>();
        final Set<File> files = new LinkedHashSet<>();
        List<Throwable> failures;

        @Override
        public void visitArtifact(DisplayName variantName, AttributeContainer variantAttributes, ImmutableCapabilities capabilities, ResolvableArtifact artifact) {
            try {
                ResolvedArtifact resolvedArtifact = artifact.toPublicView();
                files.add(resolvedArtifact.getFile());
                artifacts.add(resolvedArtifact);
            } catch (org.gradle.internal.resolve.ArtifactResolveException e) {
                //ignore
            } catch (Exception e) {
                visitFailure(e);
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
            // This is false so that we can download the artifact in `visitArtifact` and ignore missing files
            return false;
        }

        @Override
        public void visitFailure(Throwable failure) {
            if (failures == null) {
                failures = new ArrayList<>();
            }
            failures.add(failure);
        }

        public List<Throwable> getFailures() {
            return failures != null ? failures : Collections.emptyList();
        }
    }

    private static class LenientFilesAndArtifactResolveVisitor extends LenientArtifactCollectingVisitor {
        @Override
        public FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
            return FileCollectionStructureVisitor.VisitType.Visit;
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
