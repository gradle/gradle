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
import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.LocalDependencyFiles;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSetResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResults;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class DefaultLenientConfiguration implements LenientConfigurationInternal {

    private final ResolutionHost resolutionHost;
    private final VisitedGraphResults graphResults;
    private final VisitedArtifactSet artifactResults;
    private final Function<SelectedArtifactResults, TransientConfigurationResults> legacyArtifactResultsLoader;
    private final ResolvedArtifactSetResolver artifactSetResolver;
    private final ArtifactSelectionSpec implicitSelectionSpec;

    // Selected for the configuration
    private @Nullable SelectedArtifactResults artifactsForThisConfiguration;

    public DefaultLenientConfiguration(
        ResolutionHost resolutionHost,
        VisitedGraphResults graphResults,
        VisitedArtifactSet artifactResults,
        Function<SelectedArtifactResults, TransientConfigurationResults> legacyArtifactResultsLoader,
        ResolvedArtifactSetResolver artifactSetResolver,
        ArtifactSelectionSpec implicitSelectionSpec
    ) {
        this.resolutionHost = resolutionHost;
        this.graphResults = graphResults;
        this.artifactResults = artifactResults;
        this.legacyArtifactResultsLoader = legacyArtifactResultsLoader;
        this.artifactSetResolver = artifactSetResolver;
        this.implicitSelectionSpec = implicitSelectionSpec;
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

    private TransientConfigurationResults loadTransientGraphResults() {
        return legacyArtifactResultsLoader.apply(getSelectedArtifacts());
    }

    @Override
    public ImmutableSet<ResolvedDependency> getFirstLevelModuleDependencies() {
        return loadTransientGraphResults().getFirstLevelDependencies();
    }

    @Override
    public Set<ResolvedDependency> getAllModuleDependencies() {
        Set<ResolvedDependency> resolvedElements = new LinkedHashSet<>();
        Deque<ResolvedDependency> workQueue = new LinkedList<>(loadTransientGraphResults().getRootNode().getChildren());
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
        LenientArtifactCollectingVisitor visitor = new LenientArtifactCollectingVisitor();
        artifactSetResolver.visitArtifacts(getSelectedArtifacts().getArtifacts(), visitor, resolutionHost);
        resolutionHost.rethrowFailuresAndReportProblems("artifacts", visitor.getFailures());
        return visitor.artifacts;
    }

    private static class LenientArtifactCollectingVisitor implements ArtifactVisitor {

        private final Set<ResolvedArtifact> artifacts = new LinkedHashSet<>();
        private @Nullable List<Throwable> failures;

        @Override
        public void visitArtifact(DisplayName variantName, ImmutableAttributes variantAttributes, ImmutableCapabilities capabilities, ResolvableArtifact artifact) {
            try {
                ResolvedArtifact resolvedArtifact = artifact.toPublicView();

                // Attempt to download the file
                resolvedArtifact.getFile();

                // Only record the artifact if the file is accessible
                artifacts.add(resolvedArtifact);
            } catch (org.gradle.internal.resolve.ArtifactResolveException e) {
                // Ignore
                // TODO: Would be nice to not use exceptions for control flow
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

}
