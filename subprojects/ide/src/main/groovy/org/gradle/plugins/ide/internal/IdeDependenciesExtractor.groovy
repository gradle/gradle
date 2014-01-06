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
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.specs.Spec

class IdeDependenciesExtractor {

    static class IdeDependency {
        Configuration declaredConfiguration
    }

    static class IdeLocalFileDependency extends IdeDependency {
        File file
    }

    static class IdeRepoFileDependency extends IdeDependency {
        File file
        File sourceFile
        File javadocFile
        ModuleVersionIdentifier id
    }

    static class UnresolvedIdeRepoFileDependency extends IdeRepoFileDependency {
        Exception problem
    }

    static class IdeProjectDependency extends IdeDependency {
        Project project
    }

    List<IdeProjectDependency> extractProjectDependencies(Project project, Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        LinkedHashMap<Project, Configuration> depToConf = [:]
        for (plusConfiguration in plusConfigurations) {
            for (Project projectDependency in getResolvedProjectDependencies(plusConfiguration, project)) {
                depToConf[projectDependency] = plusConfiguration
            }
        }
        for (minusConfiguration in minusConfigurations) {
            for(minusDep in getResolvedProjectDependencies(minusConfiguration, project)) {
                depToConf.remove(minusDep)
            }
        }
        return depToConf.collect { projectDependency, conf ->
            new IdeProjectDependency(project: projectDependency, declaredConfiguration: conf)
        }
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
        def unresolved = new LinkedHashMap<ModuleComponentSelector, UnresolvedIdeRepoFileDependency>()
        for (c in plusConfigurations) {
            def deps = getUnresolvedModuleComponentSelectors(c)
            deps.each {
                unresolved[it] = new UnresolvedIdeRepoFileDependency(
                    file: new File(unresolvedFileName(it)), declaredConfiguration: c)
            }
        }
        for (c in minusConfigurations) {
            def deps = getUnresolvedModuleComponentSelectors(c)
            deps.each { unresolved.remove(it) }
        }
        unresolved.values()
    }

    private String unresolvedFileName(ModuleComponentSelector dep) {
        "unresolved dependency - $dep.group $dep.module $dep.version"
    }

    List<IdeLocalFileDependency> extractLocalFileDependencies(Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        LinkedHashMap<File, Configuration> fileToConf = [:]
        def filter = { it instanceof SelfResolvingDependency && !(it instanceof org.gradle.api.artifacts.ProjectDependency)}

        for (plusConfiguration in plusConfigurations) {
            def deps = plusConfiguration.allDependencies.findAll(filter)
            def files = deps.collect { it.resolve() }.flatten()
            files.each { fileToConf[it] = plusConfiguration }
        }
        for (minusConfiguration in minusConfigurations) {
            def deps = minusConfiguration.allDependencies.findAll(filter)
            def files = deps.collect { it.resolve() }.flatten()
            files.each { fileToConf.remove(it) }
        }
        return fileToConf.collect { file, conf ->
            new IdeLocalFileDependency( file: file, declaredConfiguration: conf)
        }
    }

    public Collection<IdeRepoFileDependency> resolvedExternalDependencies(Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        LinkedHashMap<File, IdeRepoFileDependency> out = [:]
        for (plusConfiguration in plusConfigurations) {
            for (artifact in plusConfiguration.resolvedConfiguration.lenientConfiguration.getArtifacts({ it instanceof ExternalDependency } as Spec)) {
                out[artifact.file] = new IdeRepoFileDependency( file: artifact.file, declaredConfiguration: plusConfiguration, id: artifact.moduleVersion.id)
            }
        }
        for (minusConfiguration in minusConfigurations) {
            for (artifact in minusConfiguration.resolvedConfiguration.lenientConfiguration.getArtifacts({ it instanceof ExternalDependency } as Spec)) {
                out.remove(artifact.file)
            }
        }
        out.values()
    }

    /**
     * Gets incoming resolution result for a given configuration.
     *
     * @param configuration Configuration
     * @return Incoming resolution result
     */
    private ResolutionResult getIncomingResolutionResult(Configuration configuration) {
        configuration.incoming.resolutionResult
    }

    /**
     * Gets resolved project dependencies for a given configuration.
     *
     * @param configuration Configuration
     * @param project Project
     * @return List of resolved project dependencies
     */
    private List<Project> getResolvedProjectDependencies(Configuration configuration, Project project) {
        ResolutionResult result = getIncomingResolutionResult(configuration)
        Collection<ResolvedDependencyResult> resolvedDependencies = result.root.dependencies.findAll { it instanceof ResolvedDependencyResult }
        Collection<ResolvedComponentResult> projectComponents = resolvedDependencies.selected.findAll { it.id instanceof ProjectComponentIdentifier }
        projectComponents.collect { project.project(it.id.projectPath) }
    }

    /**
     * Gets unresolved module component selectors for a given configuration.
     *
     * @param configuration Configuration
     * @return List of unresolved module component selectors
     */
    private Collection<ModuleComponentSelector> getUnresolvedModuleComponentSelectors(Configuration configuration) {
        ResolutionResult result = getIncomingResolutionResult(configuration)
        Collection<UnresolvedDependencyResult> unresolvedDependencies = result.root.dependencies.findAll { it instanceof UnresolvedDependencyResult }
        unresolvedDependencies.requested.findAll { it instanceof ModuleComponentSelector }
    }
}
