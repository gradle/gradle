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
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.specs.Spec
import org.gradle.api.artifacts.*

/**
 * @author: Szczepan Faber, created at: 7/1/11
 */
class IdeDependenciesExtractor {

    static class IdeDependency {
        Project dependencyProject
        File externalDependency
        File externalDependencySource
        File externalDependencyJavadoc
        File internalDependency
    }

    List<IdeDependency> extract(ConfigurationContainer confContainer, Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations, boolean downloadSources, boolean downloadJavadoc) {
        def out = []

        getDependencies(plusConfigurations, minusConfigurations, { it instanceof ProjectDependency }).each { ProjectDependency it ->
            out << new IdeDependency (dependencyProject: it.dependencyProject)
        }

        def allResolvedDependencies = resolveDependencies(plusConfigurations, minusConfigurations)

        Set sourceDependencies = getResolvableDependenciesForAllResolvedDependencies(allResolvedDependencies) { dependency ->
            addSourceArtifact(dependency)
        }
        Map<String, File> sourceFiles = downloadSources ? getFiles(confContainer.detachedConfiguration(sourceDependencies as Dependency[]), "sources") : [:]

        Set javadocDependencies = getResolvableDependenciesForAllResolvedDependencies(allResolvedDependencies) { dependency ->
            addJavadocArtifact(dependency)
        }
        Map<String, File> javadocFiles = downloadJavadoc ? getFiles(confContainer.detachedConfiguration(javadocDependencies as Dependency[]), "javadoc") : [:]

        resolveFiles(plusConfigurations, minusConfigurations).collect { File binaryFile ->
            File sourceFile = sourceFiles[binaryFile.name]
            File javadocFile = javadocFiles[binaryFile.name]
            out << new IdeDependency( externalDependency: binaryFile, externalDependencySource: sourceFile, externalDependencyJavadoc: javadocFile)
        }
        getSelfResolvingFiles(
                getDependencies(plusConfigurations, minusConfigurations, { it instanceof SelfResolvingDependency && !(it instanceof org.gradle.api.artifacts.ProjectDependency)})
        ).each {
            out << new IdeDependency( internalDependency: it)
        }

        return out
    }

    private Set<File> resolveFiles(Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        def result = new LinkedHashSet()
        for (plusConfiguration in plusConfigurations) {
            result.addAll(plusConfiguration.files { it instanceof ExternalDependency })
        }
        for (minusConfiguration in minusConfigurations) {
            result.removeAll(minusConfiguration.files { it instanceof ExternalDependency })
        }
        result
    }

    private getSelfResolvingFiles(Collection dependencies) {
        dependencies.collect { SelfResolvingDependency dependency ->
            dependency.resolve().collect()
        }.flatten()
    }


    private Set<Dependency> getDependencies(Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations, Closure filter) {
        def result = new LinkedHashSet()
        for (plusConfiguration in plusConfigurations) {
            result.addAll(plusConfiguration.allDependencies.findAll(filter))
        }
        for (minusConfiguration in minusConfigurations) {
            result.removeAll(minusConfiguration.allDependencies.findAll(filter))
        }
        result
    }

    private Set<ResolvedDependency> resolveDependencies(Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        def result = new LinkedHashSet()
        for (plusConfiguration in plusConfigurations) {
            result.addAll(getAllDeps(plusConfiguration.resolvedConfiguration.getFirstLevelModuleDependencies({ it instanceof ExternalDependency } as Spec)))
        }
        for (minusConfiguration in minusConfigurations) {
            result.removeAll(getAllDeps(minusConfiguration.resolvedConfiguration.getFirstLevelModuleDependencies({ it instanceof ExternalDependency } as Spec)))
        }
        result
    }

    protected Set getAllDeps(Collection deps, Set allDeps = []) {
        deps.each { ResolvedDependency resolvedDependency ->
            def notSeenBefore = allDeps.add(resolvedDependency)
            if (notSeenBefore) { // defend against circular dependencies
                getAllDeps(resolvedDependency.children, allDeps)
            }
        }
        allDeps
    }

    private List getResolvableDependenciesForAllResolvedDependencies(Set allResolvedDependencies, Closure configureClosure) {
        return allResolvedDependencies.collect { ResolvedDependency resolvedDependency ->
            def dependency = new DefaultExternalModuleDependency(resolvedDependency.moduleGroup, resolvedDependency.moduleName, resolvedDependency.moduleVersion,
                    resolvedDependency.configuration)
            dependency.transitive = false
            configureClosure.call(dependency)
            dependency
        }
    }

    protected void addSourceArtifact(DefaultExternalModuleDependency dependency) {
        dependency.artifact { artifact ->
            artifact.name = dependency.name
            artifact.type = 'source'
            artifact.extension = 'jar'
            artifact.classifier = 'sources'
        }
    }

    protected void addJavadocArtifact(DefaultExternalModuleDependency dependency) {
        dependency.artifact { artifact ->
            artifact.name = dependency.name
            artifact.type = 'javadoc'
            artifact.extension = 'jar'
            artifact.classifier = 'javadoc'
        }
    }

    private Map getFiles(Configuration configuration, String classifier) {
        return (Map) configuration.files.inject([:]) { result, sourceFile ->
            String key = sourceFile.name.replace("-${classifier}.jar", '.jar')
            result[key] = sourceFile
            result
        }
    }
}
