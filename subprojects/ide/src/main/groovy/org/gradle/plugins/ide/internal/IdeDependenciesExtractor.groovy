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

package org.gradle.plugins.ide.internal

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimap
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.resolution.JvmLibrary
import org.gradle.api.artifacts.resolution.JvmLibraryJavadocArtifact
import org.gradle.api.artifacts.resolution.JvmLibrarySourcesArtifact
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier
import org.gradle.plugins.ide.internal.resolver.DefaultIdeDependencyResolver
import org.gradle.plugins.ide.internal.resolver.IdeDependencyResolver
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency
import org.gradle.plugins.ide.internal.resolver.model.UnresolvedIdeRepoFileDependency

class IdeDependenciesExtractor {
    private final IdeDependencyResolver ideDependencyResolver = new DefaultIdeDependencyResolver()

    Collection<IdeProjectDependency> extractProjectDependencies(Project project, Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        LinkedHashMap<Project, IdeProjectDependency> deps = [:]

        for (plusConfiguration in plusConfigurations) {
            for(IdeProjectDependency dep in ideDependencyResolver.getIdeProjectDependencies(plusConfiguration, project)) {
                deps[dep.project] = dep
            }
        }

        for (minusConfiguration in minusConfigurations) {
            for(IdeProjectDependency dep in ideDependencyResolver.getIdeProjectDependencies(minusConfiguration, project)) {
                deps.remove(dep.project)
            }
        }

        deps.values()
    }

    Collection<IdeExtendedRepoFileDependency> extractRepoFileDependencies(
            DependencyHandler dependencyHandler,
            Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations,
            boolean downloadSources, boolean downloadJavadoc) {

        // can have multiple IDE dependencies with same component identifier (see GRADLE-1622)
        Multimap<ComponentIdentifier, IdeExtendedRepoFileDependency> resolvedDependencies = LinkedHashMultimap.create()
        for (dep in resolvedExternalDependencies(plusConfigurations, minusConfigurations)) {
            resolvedDependencies.put(toComponentIdentifier(dep.id), dep)
        }

        downloadSourcesAndJavadoc(dependencyHandler, resolvedDependencies, downloadSources, downloadJavadoc)

        def unresolvedDependencies = unresolvedExternalDependencies(plusConfigurations, minusConfigurations)
        return resolvedDependencies.values() + unresolvedDependencies
    }

    private ModuleComponentIdentifier toComponentIdentifier(ModuleVersionIdentifier id) {
        new DefaultModuleComponentIdentifier(id.group, id.name, id.version)
    }

    private void downloadSourcesAndJavadoc(DependencyHandler dependencyHandler,
                                           Multimap<ComponentIdentifier, IdeExtendedRepoFileDependency> dependencies,
                                           boolean downloadSources, boolean downloadJavadoc) {

        if (!downloadSources && !downloadJavadoc) {
            return
        }

        def query = dependencyHandler.createArtifactResolutionQuery()
        query.forComponents(dependencies.keySet());
        if (downloadSources) {
            query.withArtifacts(JvmLibrary, JvmLibrarySourcesArtifact)
        }
        if (downloadJavadoc) {
            query.withArtifacts(JvmLibrary, JvmLibraryJavadocArtifact)
        }

        def jvmLibraries = query.execute().getComponents(JvmLibrary)
        for (jvmLibrary in jvmLibraries) {
            for (dependency in dependencies.get(jvmLibrary.id)) {
                for (sourcesArtifact in jvmLibrary.sourcesArtifacts) {
                    if (sourcesArtifact.failure == null) {
                        dependency.sourceFile = sourcesArtifact.file
                    }
                }
                for (javadocArtifact in jvmLibrary.javadocArtifacts) {
                    if (javadocArtifact.failure == null) {
                        dependency.javadocFile = javadocArtifact.file
                    }
                }
            }
        }
    }

    private Collection<UnresolvedIdeRepoFileDependency> unresolvedExternalDependencies(Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        def unresolved = new LinkedHashMap<File, UnresolvedIdeRepoFileDependency>()

        for (c in plusConfigurations) {
            def deps = ideDependencyResolver.getUnresolvedIdeRepoFileDependencies(c)

            deps.each {
                unresolved[it.file] = it
            }
        }

        for (c in minusConfigurations) {
            def deps = ideDependencyResolver.getUnresolvedIdeRepoFileDependencies(c)

            deps.each {
                unresolved.remove(it.file)
            }
        }

        unresolved.values()
    }

    Collection<IdeLocalFileDependency> extractLocalFileDependencies(Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        LinkedHashMap<File, IdeLocalFileDependency> fileToConf = [:]

        for (plusConfiguration in plusConfigurations) {
            for(IdeLocalFileDependency localFileDependency in ideDependencyResolver.getIdeLocalFileDependencies(plusConfiguration)) {
                fileToConf[localFileDependency.file] = localFileDependency
            }
        }
        for (minusConfiguration in minusConfigurations) {
            for(IdeLocalFileDependency localFileDependency in ideDependencyResolver.getIdeLocalFileDependencies(minusConfiguration)) {
                fileToConf.remove(localFileDependency.file)
            }
        }

        fileToConf.values()
    }

    Collection<IdeExtendedRepoFileDependency> resolvedExternalDependencies(Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        LinkedHashMap<File, IdeExtendedRepoFileDependency> out = [:]

        for (plusConfiguration in plusConfigurations) {
            for (artifact in ideDependencyResolver.getIdeRepoFileDependencies(plusConfiguration)) {
                out[artifact.file] = artifact
            }
        }

        for (minusConfiguration in minusConfigurations) {
            for (artifact in ideDependencyResolver.getIdeRepoFileDependencies(minusConfiguration)) {
                out.remove(artifact.file)
            }
        }

        out.values()
    }
}
