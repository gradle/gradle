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
import org.gradle.api.specs.Spec
import org.gradle.api.artifacts.*

/**
 * @author: Szczepan Faber, created at: 7/1/11
 */
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

    List<IdeProjectDependency> extractProjectDependencies(Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        LinkedHashMap<ProjectDependency, Configuration> depToConf = [:]
        for (plusConfiguration in plusConfigurations) {
            for (ProjectDependency dependency in plusConfiguration.allDependencies.findAll({ it instanceof ProjectDependency })) {
                depToConf[dependency] = plusConfiguration
            }
        }
        for (minusConfiguration in minusConfigurations) {
            for(minusDep in minusConfiguration.allDependencies.findAll({ it instanceof ProjectDependency })) {
                depToConf.remove(minusDep)
            }
        }
        return depToConf.collect { projectDependency, conf ->
            new IdeProjectDependency(project: projectDependency.dependencyProject, declaredConfiguration: conf)
        }
    }

    List<IdeRepoFileDependency> extractRepoFileDependencies(ConfigurationContainer confContainer,
                                                           Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations,
                                                           boolean downloadSources, boolean downloadJavadoc) {
        def out = []

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
        def unresolved = new LinkedHashMap<String, UnresolvedIdeRepoFileDependency>()
        for (c in plusConfigurations) {
            def deps = c.resolvedConfiguration.lenientConfiguration.unresolvedModuleDependencies
            deps.each {
                unresolved[it.selector] = new UnresolvedIdeRepoFileDependency(
                    file: new File(unresolvedFileName(it)), declaredConfiguration: c)
            }
        }
        for (c in minusConfigurations) {
            def deps = c.resolvedConfiguration.lenientConfiguration.unresolvedModuleDependencies
            deps.each { unresolved.remove(it.selector) }
        }
        unresolved.values()
    }

    private String unresolvedFileName(UnresolvedDependency dep) {
        "unresolved dependency - $dep.selector.group $dep.selector.name $dep.selector.version"
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
}
