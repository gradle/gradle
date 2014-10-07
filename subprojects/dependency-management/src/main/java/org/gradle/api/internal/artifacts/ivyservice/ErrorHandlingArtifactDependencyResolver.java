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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.specs.Spec;

import java.io.File;
import java.util.List;
import java.util.Set;

public class ErrorHandlingArtifactDependencyResolver implements ArtifactDependencyResolver {
    private final ArtifactDependencyResolver dependencyResolver;

    public ErrorHandlingArtifactDependencyResolver(ArtifactDependencyResolver dependencyResolver) {
        this.dependencyResolver = dependencyResolver;
    }

    public void resolve(ConfigurationInternal configuration,
                        List<? extends ResolutionAwareRepository> repositories,
                        GlobalDependencyResolutionRules metadataHandler,
                        ResolverResults results) throws ResolveException {
        try {
            dependencyResolver.resolve(configuration, repositories, metadataHandler, results);
        } catch (final Throwable e) {
            results.failed(new BrokenResolvedConfiguration(e, configuration), wrapException(e, configuration));
            return;
        }
        ResolvedConfiguration wrappedConfiguration = new ErrorHandlingResolvedConfiguration(results.getResolvedConfiguration(), configuration);
        ResolutionResult wrappedResult = new ErrorHandlingResolutionResult(results.getResolutionResult(), configuration);
        results.resolved(wrappedConfiguration, wrappedResult);
    }

    private static ResolveException wrapException(Throwable e, Configuration configuration) {
        if (e instanceof ResolveException) {
            return (ResolveException) e;
        }
        return new ResolveException(configuration, e);
    }

    private static class ErrorHandlingLenientConfiguration implements LenientConfiguration {
        private final LenientConfiguration lenientConfiguration;
        private final Configuration configuration;

        private ErrorHandlingLenientConfiguration(LenientConfiguration lenientConfiguration, Configuration configuration) {
            this.lenientConfiguration = lenientConfiguration;
            this.configuration = configuration;
        }

        public Set<ResolvedArtifact> getArtifacts(Spec<? super Dependency> dependencySpec) {
            try {
                return lenientConfiguration.getArtifacts(dependencySpec);
            } catch (Throwable e) {
                throw wrapException(e, configuration);
            }
        }

        public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) {
            try {
                return lenientConfiguration.getFirstLevelModuleDependencies(dependencySpec);
            } catch (Throwable e) {
                throw wrapException(e, configuration);
            }
        }

        public Set<UnresolvedDependency> getUnresolvedModuleDependencies() {
            try {
                return lenientConfiguration.getUnresolvedModuleDependencies();
            } catch (Throwable e) {
                throw wrapException(e, configuration);
            }
        }

        public Set<File> getFiles(Spec<? super Dependency> dependencySpec) {
            try {
                return lenientConfiguration.getFiles(dependencySpec);
            } catch (Throwable e) {
                throw wrapException(e, configuration);
            }
        }
    }

    private static class ErrorHandlingResolutionResult implements ResolutionResult {
        private final ResolutionResult resolutionResult;
        private final Configuration configuration;

        public ErrorHandlingResolutionResult(ResolutionResult resolutionResult, Configuration configuration) {
            this.resolutionResult = resolutionResult;
            this.configuration = configuration;
        }

        public ResolvedComponentResult getRoot() {
            try {
                return resolutionResult.getRoot();
            } catch (Throwable e) {
                throw wrapException(e, configuration);
            }
        }

        public void allDependencies(Action<? super DependencyResult> action) {
            resolutionResult.allDependencies(action);
        }

        public Set<? extends DependencyResult> getAllDependencies() {
            try {
                return resolutionResult.getAllDependencies();
            } catch (Throwable e) {
                throw wrapException(e, configuration);
            }
        }

        public void allDependencies(Closure closure) {
            resolutionResult.allDependencies(closure);
        }

        public Set<ResolvedComponentResult> getAllComponents() {
            try {
                return resolutionResult.getAllComponents();
            } catch (Throwable e) {
                throw wrapException(e, configuration);
            }
        }

        public void allComponents(Action<? super ResolvedComponentResult> action) {
            resolutionResult.allComponents(action);
        }

        public void allComponents(Closure closure) {
            resolutionResult.allComponents(closure);
        }
    }
    
    private static class ErrorHandlingResolvedConfiguration implements ResolvedConfiguration {
        private final ResolvedConfiguration resolvedConfiguration;
        private final Configuration configuration;

        public ErrorHandlingResolvedConfiguration(ResolvedConfiguration resolvedConfiguration,
                                                  Configuration configuration) {
            this.resolvedConfiguration = resolvedConfiguration;
            this.configuration = configuration;
        }

        public boolean hasError() {
            return resolvedConfiguration.hasError();
        }

        public LenientConfiguration getLenientConfiguration() {
            try {
                return new ErrorHandlingLenientConfiguration(resolvedConfiguration.getLenientConfiguration(), configuration);
            } catch (Throwable e) {
                throw wrapException(e, configuration);
            }
        }

        public void rethrowFailure() throws ResolveException {
            try {
                resolvedConfiguration.rethrowFailure();
            } catch (Throwable e) {
                throw wrapException(e, configuration);
            }
        }

        public Set<File> getFiles(Spec<? super Dependency> dependencySpec) throws ResolveException {
            try {
                return resolvedConfiguration.getFiles(dependencySpec);
            } catch (Throwable e) {
                throw wrapException(e, configuration);
            }
        }

        public Set<ResolvedDependency> getFirstLevelModuleDependencies() throws ResolveException {
            try {
                return resolvedConfiguration.getFirstLevelModuleDependencies();
            } catch (Throwable e) {
                throw wrapException(e, configuration);
            }
        }

        public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) throws ResolveException {
            try {
                return resolvedConfiguration.getFirstLevelModuleDependencies(dependencySpec);
            } catch (Throwable e) {
                throw wrapException(e, configuration);
            }
        }

        public Set<ResolvedArtifact> getResolvedArtifacts() throws ResolveException {
            try {
                return resolvedConfiguration.getResolvedArtifacts();
            } catch (Throwable e) {
                throw wrapException(e, configuration);
            }
        }
    }

    private static class BrokenResolvedConfiguration implements ResolvedConfiguration {
        private final Throwable e;
        private final Configuration configuration;

        public BrokenResolvedConfiguration(Throwable e, Configuration configuration) {
            this.e = e;
            this.configuration = configuration;
        }

        public boolean hasError() {
            return true;
        }

        public LenientConfiguration getLenientConfiguration() {
            throw wrapException(e, configuration);
        }

        public void rethrowFailure() throws ResolveException {
            throw wrapException(e, configuration);
        }

        public Set<File> getFiles(Spec<? super Dependency> dependencySpec) throws ResolveException {
            throw wrapException(e, configuration);
        }

        public Set<ResolvedDependency> getFirstLevelModuleDependencies() throws ResolveException {
            throw wrapException(e, configuration);
        }

        public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) throws ResolveException {
            throw wrapException(e, configuration);
        }

        public Set<ResolvedArtifact> getResolvedArtifacts() throws ResolveException {
            throw wrapException(e, configuration);
        }
    }
}
