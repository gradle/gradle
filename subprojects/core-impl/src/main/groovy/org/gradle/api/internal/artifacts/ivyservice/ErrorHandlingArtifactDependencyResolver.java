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

import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.specs.Spec;

import java.io.File;
import java.util.Set;

public class ErrorHandlingArtifactDependencyResolver implements ArtifactDependencyResolver {
    private final ArtifactDependencyResolver dependencyResolver;

    public ErrorHandlingArtifactDependencyResolver(ArtifactDependencyResolver dependencyResolver) {
        this.dependencyResolver = dependencyResolver;
    }

    public ResolverResults resolve(final ConfigurationInternal configuration) {
        final ResolverResults results;
        try {
            results = dependencyResolver.resolve(configuration);
        } catch (final Throwable e) {
            return new ResolverResults(new BrokenResolvedConfiguration(e, configuration), wrapException(e, configuration));
        }
        ResolvedConfiguration withErrorHandling = new ErrorHandlingResolvedConfiguration(results.getResolvedConfiguration(), configuration);
        return results.withResolvedConfiguration(withErrorHandling);
    }

    private static ResolveException wrapException(Throwable e, Configuration configuration) {
        if (e instanceof ResolveException) {
            return (ResolveException) e;
        }
        return new ResolveException(configuration, e);
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
            return resolvedConfiguration.getLenientConfiguration();
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
