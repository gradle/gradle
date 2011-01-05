/*
 * Copyright 2010 the original author or authors.
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

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.CachingDependencyResolveContext;
import org.gradle.api.internal.artifacts.DependencyInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class SelfResolvingDependencyResolver implements IvyDependencyResolver {
    private final IvyDependencyResolver resolver;

    public SelfResolvingDependencyResolver(IvyDependencyResolver resolver) {
        this.resolver = resolver;
    }

    public IvyDependencyResolver getResolver() {
        return resolver;
    }

    public ResolvedConfiguration resolve(final Configuration configuration, Ivy ivy, ModuleDescriptor moduleDescriptor) {
        final ResolvedConfiguration resolvedConfiguration = resolver.resolve(configuration, ivy, moduleDescriptor);
        final Set<DependencyInternal> dependencies = configuration.getAllDependencies(DependencyInternal.class);

        return new ResolvedConfiguration() {
            private final CachingDependencyResolveContext resolveContext = new CachingDependencyResolveContext(configuration.isTransitive());

            public Set<File> getFiles(Spec<Dependency> dependencySpec) {
                Set<File> files = new LinkedHashSet<File>();

                Set<DependencyInternal> selectedDependencies = Specs.filterIterable(dependencies, dependencySpec);
                for (DependencyInternal dependency : selectedDependencies) {
                    resolveContext.add(dependency);
                }
                files.addAll(resolveContext.resolve().getFiles());
                files.addAll(resolvedConfiguration.getFiles(dependencySpec));
                return files;
            }

            public Set<ResolvedArtifact> getResolvedArtifacts() {
                return resolvedConfiguration.getResolvedArtifacts();
            }

            public Set<ResolvedDependency> getFirstLevelModuleDependencies() {
                return resolvedConfiguration.getFirstLevelModuleDependencies();
            }

            public boolean hasError() {
                return resolvedConfiguration.hasError();
            }

            public void rethrowFailure() throws GradleException {
                resolvedConfiguration.rethrowFailure();
            }
        };
    }
}
