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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.DefaultResolverResults;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ResolveExceptionContextualizer;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.result.ResolutionResultInternal;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.model.AttributeConfigurationSelector;

import java.io.File;
import java.util.List;
import java.util.Set;

public class ErrorHandlingConfigurationResolver implements ConfigurationResolver {
    private final ConfigurationResolver delegate;
    private final ResolveExceptionContextualizer contextualizer;

    public ErrorHandlingConfigurationResolver(ConfigurationResolver delegate, ResolveExceptionContextualizer contextualizer) {
        this.delegate = delegate;
        this.contextualizer = contextualizer;
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
            return DefaultResolverResults.failed(e, contextualizer.contextualize(e, resolveContext));
        }
    }

    @Override
    public ResolverResults resolveGraph(ResolveContext resolveContext) throws ResolveException {
        ResolverResults results;
        try {
            results = delegate.resolveGraph(resolveContext);
        } catch (Exception e) {
            return DefaultResolverResults.failed(e, contextualizer.contextualize(e, resolveContext));
        }

        return results.updateResolutionResult(result -> new ErrorHandlingResolutionResult(result, resolveContext, contextualizer));
    }

    @Override
    public ResolverResults resolveArtifacts(ResolveContext resolveContext, ResolverResults graphResults) throws ResolveException {
        ResolverResults artifactResults;
        try {
            artifactResults = delegate.resolveArtifacts(resolveContext, graphResults);
        } catch (Exception e) {
            return DefaultResolverResults.failed(graphResults.getResolutionResult(), graphResults.getResolvedLocalComponents(), e, contextualizer.contextualize(e, resolveContext));
        }

        ResolvedConfiguration wrappedConfiguration = new ErrorHandlingResolvedConfiguration(artifactResults.getResolvedConfiguration(), resolveContext, contextualizer);
        return DefaultResolverResults.artifactsResolved(graphResults.getResolutionResult(), graphResults.getResolvedLocalComponents(), wrappedConfiguration, artifactResults.getVisitedArtifacts());
    }

    @Override
    public AttributeConfigurationSelector getAttributeConfigurationSelector() {
        return delegate.getAttributeConfigurationSelector();
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

    private static class ErrorHandlingResolutionResult implements ResolutionResultInternal {
        private final ResolutionResultInternal resolutionResult;
        private final ResolveContext resolveContext;
        private final ResolveExceptionContextualizer contextualizer;

        public ErrorHandlingResolutionResult(
            ResolutionResult resolutionResult,
            ResolveContext configuration,
            ResolveExceptionContextualizer contextualizer
        ) {
            this.resolutionResult = (ResolutionResultInternal) resolutionResult;
            this.resolveContext = configuration;
            this.contextualizer = contextualizer;
        }

        @Override
        public ResolvedComponentResult getRoot() {
            try {
                return resolutionResult.getRoot();
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public Provider<ResolvedComponentResult> getRootComponent() {
            return new DefaultProvider<>(this::getRoot);
        }

        @Override
        public Provider<Throwable> getNonFatalFailure() {
            return resolutionResult.getNonFatalFailure().map(e -> contextualizer.contextualize(e, resolveContext));
        }

        @Override
        public void allDependencies(Action<? super DependencyResult> action) {
            resolutionResult.allDependencies(action);
        }

        @Override
        public Set<? extends DependencyResult> getAllDependencies() {
            try {
                return resolutionResult.getAllDependencies();
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        @SuppressWarnings("rawtypes")
        public void allDependencies(Closure closure) {
            resolutionResult.allDependencies(closure);
        }

        @Override
        public Set<ResolvedComponentResult> getAllComponents() {
            try {
                return resolutionResult.getAllComponents();
            } catch (Exception e) {
                throw contextualizer.contextualize(e, resolveContext);
            }
        }

        @Override
        public void allComponents(Action<? super ResolvedComponentResult> action) {
            resolutionResult.allComponents(action);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public void allComponents(Closure closure) {
            resolutionResult.allComponents(closure);
        }

        @Override
        public AttributeContainer getRequestedAttributes() {
            return resolutionResult.getRequestedAttributes();
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

}
