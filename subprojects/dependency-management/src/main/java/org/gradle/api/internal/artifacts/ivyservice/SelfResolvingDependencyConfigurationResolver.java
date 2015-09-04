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

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.CachingDependencyResolveContext;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class SelfResolvingDependencyConfigurationResolver implements ConfigurationResolver {
    private final ConfigurationResolver delegate;

    public SelfResolvingDependencyConfigurationResolver(ConfigurationResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public void resolve(ConfigurationInternal configuration, ResolverResults results) throws ResolveException {
        delegate.resolve(configuration, results);
    }

    @Override
    public void resolveArtifacts(ConfigurationInternal configuration, ResolverResults results) throws ResolveException {

        delegate.resolveArtifacts(configuration, results);

        ResolvedConfiguration resolvedConfiguration = results.getResolvedConfiguration();
        Set<Dependency> dependencies = configuration.getAllDependencies();
        CachingDependencyResolveContext resolveContext = new CachingDependencyResolveContext(configuration.isTransitive());
        SelfResolvingFilesProvider provider = new SelfResolvingFilesProvider(resolveContext, dependencies);

        results.withResolvedConfiguration(new FilesAggregatingResolvedConfiguration(resolvedConfiguration, provider));
    }

    protected static class SelfResolvingFilesProvider {

        final CachingDependencyResolveContext resolveContext;
        final Set<Dependency> dependencies;

        public SelfResolvingFilesProvider(CachingDependencyResolveContext resolveContext, Set<Dependency> dependencies) {
            this.resolveContext = resolveContext;
            this.dependencies = dependencies;
        }

        Set<File> getFiles(Spec<? super Dependency> dependencySpec) {
            Set<Dependency> selectedDependencies = CollectionUtils.filter(dependencies, dependencySpec);
            for (Dependency dependency : selectedDependencies) {
                resolveContext.add(dependency);
            }
            return resolveContext.resolve().getFiles();
        }
    }

    protected static class FilesAggregatingResolvedConfiguration implements ResolvedConfiguration {
        final ResolvedConfiguration resolvedConfiguration;
        final SelfResolvingFilesProvider selfResolvingFilesProvider;

        FilesAggregatingResolvedConfiguration(ResolvedConfiguration resolvedConfiguration, SelfResolvingFilesProvider selfResolvingFilesProvider) {
            this.resolvedConfiguration = resolvedConfiguration;
            this.selfResolvingFilesProvider = selfResolvingFilesProvider;
        }

        public Set<File> getFiles(Spec<? super Dependency> dependencySpec) {
            Set<File> files = new LinkedHashSet<File>();
            files.addAll(selfResolvingFilesProvider.getFiles(dependencySpec));
            files.addAll(resolvedConfiguration.getFiles(dependencySpec));
            return files;
        }

        public Set<ResolvedArtifact> getResolvedArtifacts() {
            return resolvedConfiguration.getResolvedArtifacts();
        }

        public Set<ResolvedDependency> getFirstLevelModuleDependencies() {
            return resolvedConfiguration.getFirstLevelModuleDependencies();
        }

        public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) throws ResolveException {
            return resolvedConfiguration.getFirstLevelModuleDependencies(dependencySpec);
        }

        public boolean hasError() {
            return resolvedConfiguration.hasError();
        }

        public LenientConfiguration getLenientConfiguration() {
            return resolvedConfiguration.getLenientConfiguration();
        }

        public void rethrowFailure() throws GradleException {
            resolvedConfiguration.rethrowFailure();
        }
    }
}
