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

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.plugins.ide.internal.resolver.DefaultIdeDependencyResolver
import org.gradle.plugins.ide.internal.resolver.IdeDependencyResolver
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency
import org.gradle.plugins.ide.internal.resolver.model.IdeRepoFileDependency
import org.gradle.plugins.ide.internal.resolver.model.UnresolvedIdeRepoFileDependency

class IdeDependenciesExtractor {
    IdeDependencyResolver ideDependencyResolver = new DefaultIdeDependencyResolver()

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

    List<IdeRepoFileDependency> extractRepoFileDependencies(ConfigurationContainer confContainer,
                                                           Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations,
                                                           boolean downloadSources, boolean downloadJavadoc) {
        List<IdeRepoFileDependency> out = []

        def downloader = new JavadocAndSourcesDownloader(confContainer, plusConfigurations, minusConfigurations, downloadSources, downloadJavadoc)

        resolvedExternalDependencies(plusConfigurations, minusConfigurations).each { IdeRepoFileDependency dependency ->
            dependency.sourceFile = downloader.sourceFor(dependency.file.name)
            dependency.javadocFile = downloader.javadocFor(dependency.file.name)
            out << dependency
        }

        out.addAll(unresolvedExternalDependencies(plusConfigurations, minusConfigurations))

        out
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

    Collection<IdeRepoFileDependency> resolvedExternalDependencies(Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        LinkedHashMap<File, IdeRepoFileDependency> out = [:]

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
