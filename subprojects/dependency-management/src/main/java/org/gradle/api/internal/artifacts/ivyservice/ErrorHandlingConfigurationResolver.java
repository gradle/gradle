/*
 * Copyright 2015 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.DefaultResolverResults;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ResolveExceptionContextualizer;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResult;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;
import org.gradle.api.specs.Spec;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Handles fatal and non-fatal exceptions thrown by a delegate resolver.
 */
public class ErrorHandlingConfigurationResolver implements ConfigurationResolver {
    private final ConfigurationResolver delegate;
    private final ResolveExceptionContextualizer exceptionMapper;

    public ErrorHandlingConfigurationResolver(ConfigurationResolver delegate, ResolveExceptionContextualizer exceptionMapper) {
        this.delegate = delegate;
        this.exceptionMapper = exceptionMapper;
    }

    @Override
    public List<ResolutionAwareRepository> getRepositories() {
        return delegate.getRepositories();
    }

    @Override
    public ResolverResults resolveBuildDependencies(ResolveContext resolveContext) {
        try {
            return delegate.resolveBuildDependencies(resolveContext);
        } catch (Exception e) {
            return new BrokenResolverResults(exceptionMapper.contextualize(e, resolveContext));
        }
    }

    @Override
    public ResolverResults resolveGraph(ResolveContext resolveContext) throws ResolveException {
        try {
            return delegate.resolveGraph(resolveContext);
        } catch (Exception e) {
            return new BrokenResolverResults(exceptionMapper.contextualize(e, resolveContext));
        }
    }

    @Override
    public ResolverResults resolveArtifacts(ResolveContext resolveContext, ResolverResults graphResults) throws ResolveException {
        ResolverResults artifactResults;
        try {
            artifactResults = delegate.resolveArtifacts(resolveContext, graphResults);
        } catch (Exception e) {
            return new BrokenResolverResults(exceptionMapper.contextualize(e, resolveContext));
        }

        // Handle non-fatal failures in old model (ResolvedConfiguration) with `ErrorHandling` wrappers.
        // The new model (ResolutionResult) handles non-fatal failure without needing wrappers.
        ResolvedConfiguration wrappedConfiguration = new ErrorHandlingResolvedConfiguration(artifactResults.getResolvedConfiguration(), resolveContext, exceptionMapper);
        return DefaultResolverResults.artifactsResolved(
            graphResults.getMinimalResolutionResult(),
            graphResults.getResolvedLocalComponents(),
            wrappedConfiguration,
            artifactResults.getVisitedArtifacts()
        );
    }

    private static class ErrorHandlingLenientConfiguration implements LenientConfiguration {
        private final LenientConfiguration lenientConfiguration;
        private final ResolveContext resolveContext;
        private final ResolveExceptionContextualizer contextualizer;

        private ErrorHandlingLenientConfiguration(
            LenientConfiguration lenientConfiguration,
            ResolveContext resolveContext,
            ResolveExceptionContextualizer contextualizer
        ) {
            this.lenientConfiguration = lenientConfiguration;
            this.resolveContext = resolveContext;
            this.contextualizer = contextualizer;
        }

        @Override
        public Set<ResolvedArtifact> getArtifacts() {
            try {
                return lenientConfiguration.getArtifacts();
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<ResolvedArtifact> getArtifacts(Spec<? super Dependency> dependencySpec) {
            try {
                return lenientConfiguration.getArtifacts(dependencySpec);
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<ResolvedDependency> getFirstLevelModuleDependencies() {
            try {
                return lenientConfiguration.getFirstLevelModuleDependencies();
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) {
            try {
                return lenientConfiguration.getFirstLevelModuleDependencies(dependencySpec);
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<ResolvedDependency> getAllModuleDependencies() {
            try {
                return lenientConfiguration.getAllModuleDependencies();
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<UnresolvedDependency> getUnresolvedModuleDependencies() {
            try {
                return lenientConfiguration.getUnresolvedModuleDependencies();
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<File> getFiles() {
            try {
                return lenientConfiguration.getFiles();
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<File> getFiles(Spec<? super Dependency> dependencySpec) {
            try {
                return lenientConfiguration.getFiles(dependencySpec);
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }
    }

    private static class ErrorHandlingResolvedConfiguration implements ResolvedConfiguration {
        private final ResolvedConfiguration resolvedConfiguration;
        private final ResolveContext resolveContext;
        private final ResolveExceptionContextualizer contextualizer;

        public ErrorHandlingResolvedConfiguration(
            ResolvedConfiguration resolvedConfiguration,
            ResolveContext resolveContext,
            ResolveExceptionContextualizer contextualizer
        ) {
            this.resolvedConfiguration = resolvedConfiguration;
            this.resolveContext = resolveContext;
            this.contextualizer = contextualizer;
        }

        @Override
        public boolean hasError() {
            return resolvedConfiguration.hasError();
        }

        @Override
        public LenientConfiguration getLenientConfiguration() {
            try {
                return new ErrorHandlingLenientConfiguration(resolvedConfiguration.getLenientConfiguration(), resolveContext, contextualizer);
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public void rethrowFailure() throws ResolveException {
            try {
                resolvedConfiguration.rethrowFailure();
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<File> getFiles() throws ResolveException {
            try {
                return resolvedConfiguration.getFiles();
            } catch (ResolveException e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<File> getFiles(Spec<? super Dependency> dependencySpec) throws ResolveException {
            try {
                return resolvedConfiguration.getFiles(dependencySpec);
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<ResolvedDependency> getFirstLevelModuleDependencies() throws ResolveException {
            try {
                return resolvedConfiguration.getFirstLevelModuleDependencies();
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) throws ResolveException {
            try {
                return resolvedConfiguration.getFirstLevelModuleDependencies(dependencySpec);
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<ResolvedArtifact> getResolvedArtifacts() throws ResolveException {
            try {
                return resolvedConfiguration.getResolvedArtifacts();
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }
    }

    @VisibleForTesting
    public static class BrokenResolverResults implements ResolverResults {

        private final ResolveException failure;

        public BrokenResolverResults(ResolveException failure) {
            this.failure = failure;
        }

        @Override
        public boolean hasError() {
            return true;
        }

        @Override
        public ResolvedConfiguration getResolvedConfiguration() {
            return new BrokenResolvedConfiguration(failure);
        }

        @Override
        public VisitedArtifactSet getVisitedArtifacts() {
            throw failure;
        }

        @Override
        public ResolvedLocalComponentsResult getResolvedLocalComponents() {
            throw failure;
        }

        @Override
        public ArtifactResolveState getArtifactResolveState() {
            throw failure;
        }

        @Override
        public ResolveException getFailure() {
            return failure;
        }

        @Override
        public MinimalResolutionResult getMinimalResolutionResult() {
            throw failure;
        }
    }

    private static class BrokenResolvedConfiguration implements ResolvedConfiguration {
        private final ResolveException failure;

        public BrokenResolvedConfiguration(ResolveException failure) {
            this.failure = failure;
        }

        @Override
        public boolean hasError() {
            return true;
        }

        @Override
        public LenientConfiguration getLenientConfiguration() {
            throw failure;
        }

        @Override
        public void rethrowFailure() throws ResolveException {
            throw failure;
        }

        @Override
        public Set<File> getFiles() throws ResolveException {
            throw failure;
        }

        @Override
        public Set<File> getFiles(Spec<? super Dependency> dependencySpec) throws ResolveException {
            throw failure;
        }

        @Override
        public Set<ResolvedDependency> getFirstLevelModuleDependencies() throws ResolveException {
            throw failure;
        }

        @Override
        public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) throws ResolveException {
            throw failure;
        }

        @Override
        public Set<ResolvedArtifact> getResolvedArtifacts() throws ResolveException {
            throw failure;
        }
    }

}
