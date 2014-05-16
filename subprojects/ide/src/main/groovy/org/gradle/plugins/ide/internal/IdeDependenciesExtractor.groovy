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
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.language.java.artifact.JavadocArtifact
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier
import org.gradle.plugins.ide.internal.resolver.DefaultIdeDependencyResolver
import org.gradle.plugins.ide.internal.resolver.IdeDependencyResolver
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency
import org.gradle.plugins.ide.internal.resolver.model.UnresolvedIdeRepoFileDependency
import org.gradle.runtime.jvm.JvmLibrary

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
            query.withArtifacts(JvmLibrary, SourcesArtifact)
        }
        if (downloadJavadoc) {
            query.withArtifacts(JvmLibrary, JavadocArtifact)
        }

        def componentResults = query.execute().getResolvedComponents()
        for (componentResult in componentResults) {
            for (dependency in dependencies.get(componentResult.id)) {
                for (sourcesResult in componentResult.getArtifacts(SourcesArtifact)) {
                    if (sourcesResult instanceof ResolvedArtifactResult) {
                        dependency.sourceFile = sourcesResult.file
                    }
                }
                for (javadocResult in componentResult.getArtifacts(JavadocArtifact)) {
                    if (javadocResult instanceof ResolvedArtifactResult) {
                        dependency.javadocFile = javadocResult.file
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
