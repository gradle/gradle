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
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.DefaultResolverResults;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ResolveExceptionContextualizer;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.DefaultVisitedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResult;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.specs.Spec;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

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
        ResolverResults results;
        try {
            results = delegate.resolveGraph(resolveContext);
        } catch (Exception e) {
            return new BrokenResolverResults(exceptionMapper.contextualize(e, resolveContext));
        }

        // Handle non-fatal failures in old model (ResolvedConfiguration) with `ErrorHandling` wrappers.
        // The new model (ResolutionResult) handles non-fatal failure without needing wrappers.
        @SuppressWarnings("deprecation")
        org.gradle.api.artifacts.ResolvedConfiguration wrappedConfiguration = new ErrorHandlingResolvedConfiguration(results.getResolvedConfiguration(), resolveContext, exceptionMapper);
        return DefaultResolverResults.graphResolved(
            results.getVisitedGraph(),
            results.getResolvedLocalComponents(),
            wrappedConfiguration,
            results.getVisitedArtifacts()
        );
    }

    @Deprecated
    private static class ErrorHandlingLenientConfiguration implements org.gradle.api.artifacts.LenientConfiguration {
        private final org.gradle.api.artifacts.LenientConfiguration lenientConfiguration;
        private final ResolveContext resolveContext;
        private final ResolveExceptionContextualizer contextualizer;

        private ErrorHandlingLenientConfiguration(
            org.gradle.api.artifacts.LenientConfiguration lenientConfiguration,
            ResolveContext resolveContext,
            ResolveExceptionContextualizer contextualizer
        ) {
            this.lenientConfiguration = lenientConfiguration;
            this.resolveContext = resolveContext;
            this.contextualizer = contextualizer;
        }

        @Override
        public Set<org.gradle.api.artifacts.ResolvedArtifact> getArtifacts() {
            try {
                return lenientConfiguration.getArtifacts();
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<org.gradle.api.artifacts.ResolvedArtifact> getArtifacts(Spec<? super Dependency> dependencySpec) {
            try {
                return lenientConfiguration.getArtifacts(dependencySpec);
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<org.gradle.api.artifacts.ResolvedDependency> getFirstLevelModuleDependencies() {
            try {
                return lenientConfiguration.getFirstLevelModuleDependencies();
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<org.gradle.api.artifacts.ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) {
            try {
                return lenientConfiguration.getFirstLevelModuleDependencies(dependencySpec);
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<org.gradle.api.artifacts.ResolvedDependency> getAllModuleDependencies() {
            try {
                return lenientConfiguration.getAllModuleDependencies();
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<org.gradle.api.artifacts.UnresolvedDependency> getUnresolvedModuleDependencies() {
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

    @Deprecated
    private static class ErrorHandlingResolvedConfiguration implements org.gradle.api.artifacts.ResolvedConfiguration {
        private final org.gradle.api.artifacts.ResolvedConfiguration resolvedConfiguration;
        private final ResolveContext resolveContext;
        private final ResolveExceptionContextualizer contextualizer;

        public ErrorHandlingResolvedConfiguration(
            org.gradle.api.artifacts.ResolvedConfiguration resolvedConfiguration,
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
        public org.gradle.api.artifacts.LenientConfiguration getLenientConfiguration() {
            try {
                @SuppressWarnings("deprecation")
                ErrorHandlingLenientConfiguration errorHandlingLenientConfiguration = new ErrorHandlingLenientConfiguration(resolvedConfiguration.getLenientConfiguration(), resolveContext, contextualizer);
                return errorHandlingLenientConfiguration;
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
        public Set<org.gradle.api.artifacts.ResolvedDependency> getFirstLevelModuleDependencies() throws ResolveException {
            try {
                return resolvedConfiguration.getFirstLevelModuleDependencies();
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<org.gradle.api.artifacts.ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) throws ResolveException {
            try {
                return resolvedConfiguration.getFirstLevelModuleDependencies(dependencySpec);
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Set<org.gradle.api.artifacts.ResolvedArtifact> getResolvedArtifacts() throws ResolveException {
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
        @Deprecated
        public org.gradle.api.artifacts.ResolvedConfiguration getResolvedConfiguration() {
            return new BrokenResolvedConfiguration(failure);
        }

        @Override
        public VisitedGraphResults getVisitedGraph() {
            return new DefaultVisitedGraphResults(
                new BrokenMinimalResolutionResult(failure),
                Collections.emptySet(),
                failure
            );
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
        public boolean isFullyResolved() {
            return false;
        }
    }

    private static class BrokenMinimalResolutionResult implements MinimalResolutionResult {

        private final ResolveException failure;

        public BrokenMinimalResolutionResult(ResolveException failure) {
            this.failure = failure;
        }

        @Override
        public Supplier<ResolvedComponentResult> getRootSource() {
            return () -> {
                throw failure;
            };
        }

        @Override
        public ImmutableAttributes getRequestedAttributes() {
            throw failure;
        }
    }

    @Deprecated
    private static class BrokenResolvedConfiguration implements org.gradle.api.artifacts.ResolvedConfiguration {
        private final ResolveException failure;

        public BrokenResolvedConfiguration(ResolveException failure) {
            this.failure = failure;
        }

        @Override
        public boolean hasError() {
            return true;
        }

        @Override
        public org.gradle.api.artifacts.LenientConfiguration getLenientConfiguration() {
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
        public Set<org.gradle.api.artifacts.ResolvedDependency> getFirstLevelModuleDependencies() throws ResolveException {
            throw failure;
        }

        @Override
        public Set<org.gradle.api.artifacts.ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) throws ResolveException {
            throw failure;
        }

        @Override
        public Set<org.gradle.api.artifacts.ResolvedArtifact> getResolvedArtifacts() throws ResolveException {
            throw failure;
        }
    }

}
