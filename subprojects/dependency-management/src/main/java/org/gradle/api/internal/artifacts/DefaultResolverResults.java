/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolveState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphValidationException;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;
import java.util.function.Function;

/**
 * Default implementation of {@link ResolverResults}.
 */
public class DefaultResolverResults implements ResolverResults {

    private final ResolvedLocalComponentsResult resolvedLocalComponentsResult;
    private final ResolutionResult resolutionResult;
    private final ResolvedConfiguration resolvedConfiguration;
    private final VisitedArtifactSet visitedArtifacts;
    private final ArtifactResolveState artifactResolveState;

    private final ResolveException failure;

    public DefaultResolverResults(
        @Nullable ResolvedLocalComponentsResult resolvedLocalComponentsResult,
        @Nullable ResolutionResult resolutionResult,
        VisitedArtifactSet visitedArtifacts,
        @Nullable ResolvedConfiguration resolvedConfiguration,
        @Nullable ArtifactResolveState artifactResolveState,
        @Nullable ResolveException failure
    ) {
        this.resolvedLocalComponentsResult = resolvedLocalComponentsResult;
        this.resolutionResult = resolutionResult;
        this.visitedArtifacts = visitedArtifacts;
        this.resolvedConfiguration = resolvedConfiguration;
        this.artifactResolveState = artifactResolveState;
        this.failure = failure;
    }

    @Override
    public boolean hasError() {
        if (failure != null) {
            return true;
        }
        return resolvedConfiguration != null && resolvedConfiguration.hasError();
    }

    @Override
    public ResolvedConfiguration getResolvedConfiguration() {
        if (resolvedConfiguration == null) {
            throw new IllegalStateException("Cannot get ResolvedConfiguration before graph resolution.");
        }
        return resolvedConfiguration;
    }

    @Override
    public ResolutionResult getResolutionResult() {
        maybeRethrowFatalError();
        return resolutionResult;
    }

    @Override
    public ResolvedLocalComponentsResult getResolvedLocalComponents() {
        maybeRethrowFatalError();
        return resolvedLocalComponentsResult;
    }

    @Override
    public ArtifactResolveState getArtifactResolveState() {
        maybeRethrowAnyError();
        return artifactResolveState;
    }

    @Override
    public VisitedArtifactSet getVisitedArtifacts() {
        maybeRethrowFatalError();
        return visitedArtifacts;
    }

    public void maybeRethrowFatalError() {
        if (failure != null && isFatalError(failure)) {
            throw failure;
        }
    }

    private static boolean isFatalError(ResolveException failure) {
        boolean isNonFatal = failure.getCause() instanceof GraphValidationException;
        return !isNonFatal;
    }

    private void maybeRethrowAnyError() {
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public ResolverResults withoutNonFatalFailure() {
        if (failure == null || isFatalError(failure)) {
            return this;
        }

        return new DefaultResolverResults(
            resolvedLocalComponentsResult,
            resolutionResult,
            visitedArtifacts,
            resolvedConfiguration,
            artifactResolveState,
            null
        );
    }

    @Override
    public Throwable getNonFatalFailure() {
        return failure != null && !isFatalError(failure) ? failure : null;
    }

    @Override
    public ResolveException getFailure() {
        return failure;
    }

    @Override
    public ResolverResults withFailure(ResolveException resolveException) {
        return new DefaultResolverResults(
            resolvedLocalComponentsResult,
            resolutionResult,
            visitedArtifacts,
            resolvedConfiguration,
            artifactResolveState,
            resolveException
        );
    }

    public ResolverResults updateResolutionResult(Function<ResolutionResult, ResolutionResult> updater) {
        return new DefaultResolverResults(
            resolvedLocalComponentsResult,
            updater.apply(resolutionResult),
            visitedArtifacts,
            resolvedConfiguration,
            artifactResolveState,
            failure
        );
    }

    /**
     * Create a new result representing the result of resolving build dependencies.
     */
    public static ResolverResults buildDependenciesResolved(
        ResolutionResult resolutionResult,
        ResolvedLocalComponentsResult resolvedLocalComponentsResult,
        VisitedArtifactSet visitedArtifacts
    ) {
        return new DefaultResolverResults(
            resolvedLocalComponentsResult,
            resolutionResult,
            visitedArtifacts,
            null,
            null,
            null
        );
    }

    /**
     * Create a new result representing the result of resolving the dependency graph.
     */
    public static ResolverResults graphResolved(
        ResolutionResult resolutionResult,
        ResolvedLocalComponentsResult resolvedLocalComponentsResult,
        VisitedArtifactSet visitedArtifacts,
        @Nullable ArtifactResolveState artifactResolveState
    ) {
        return new DefaultResolverResults(
            resolvedLocalComponentsResult,
            resolutionResult,
            visitedArtifacts,
            null,
            artifactResolveState,
            null
        );
    }

    /**
     * Create a new result representing the result of resolving the artifacts.
     */
    public static ResolverResults artifactsResolved(ResolverResults graphResults, ResolvedConfiguration resolvedConfiguration, VisitedArtifactSet visitedArtifacts) {
        return new DefaultResolverResults(
            graphResults.getResolvedLocalComponents(),
            graphResults.getResolutionResult(),
            visitedArtifacts,
            resolvedConfiguration,
            null, // Do not need to keep the artifact resolve state around after artifact resolution
            null
        );
    }

    /**
     * Create a new result representing a failure to resolve the dependency graph.
     */
    public static ResolverResults failed(Exception failure, ResolveException contextualizedFailure) {
        BrokenResolvedConfiguration broken = new BrokenResolvedConfiguration(failure, contextualizedFailure);
        return new DefaultResolverResults(
            null,
            null,
            broken,
            broken,
            null,
            contextualizedFailure
        );
    }

    /**
     * Create a new result representing a failure to resolve the artifacts of a resolved dependency graph.
     */
    public static ResolverResults failed(ResolverResults graphResults, Exception failure, ResolveException contextualizedFailure) {
        BrokenResolvedConfiguration broken = new BrokenResolvedConfiguration(failure, contextualizedFailure);
        return artifactsResolved(graphResults, broken, broken).withFailure(contextualizedFailure);
    }

    /**
     * Allows resolution failures to be visited.
     */
    private static class BrokenResolvedConfiguration implements ResolvedConfiguration, VisitedArtifactSet, SelectedArtifactSet {
        private final Throwable originalException;
        private final ResolveException contextualizedException;

        public BrokenResolvedConfiguration(Throwable originalException, ResolveException contextualizedException) {
            this.originalException = originalException;
            this.contextualizedException = contextualizedException;
        }

        @Override
        public boolean hasError() {
            return true;
        }

        @Override
        public LenientConfiguration getLenientConfiguration() {
            throw contextualizedException;
        }

        @Override
        public void rethrowFailure() throws ResolveException {
            throw contextualizedException;
        }

        @Override
        public Set<File> getFiles() throws ResolveException {
            throw contextualizedException;
        }

        @Override
        public Set<File> getFiles(Spec<? super Dependency> dependencySpec) throws ResolveException {
            throw contextualizedException;
        }

        @Override
        public Set<ResolvedDependency> getFirstLevelModuleDependencies() throws ResolveException {
            throw contextualizedException;
        }

        @Override
        public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) throws ResolveException {
            throw contextualizedException;
        }

        @Override
        public Set<ResolvedArtifact> getResolvedArtifacts() throws ResolveException {
            throw contextualizedException;
        }

        @Override
        public SelectedArtifactSet select(Spec<? super Dependency> dependencySpec, AttributeContainerInternal requestedAttributes, Spec<? super ComponentIdentifier> componentSpec, boolean allowNoMatchingVariant, boolean selectFromAllVariants) {
            return this;
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            context.visitFailure(originalException);
        }

        @Override
        public void visitArtifacts(ArtifactVisitor visitor, boolean continueOnSelectionFailure) {
            visitor.visitFailure(originalException);
        }
    }
}
