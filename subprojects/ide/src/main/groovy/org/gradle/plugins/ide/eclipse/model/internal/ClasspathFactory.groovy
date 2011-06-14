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
package org.gradle.plugins.ide.eclipse.model.internal

import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.specs.Spec
import org.gradle.api.artifacts.*
import org.gradle.plugins.ide.eclipse.model.*

/**
 * @author Hans Dockter
 */
class ClasspathFactory {

    private final sourceFoldersCreator = new SourceFoldersCreator()
    private final classFoldersCreator = new ClassFoldersCreator()

    List<ClasspathEntry> createEntries(EclipseClasspath classpath) {
        def entries = []
        entries.add(new Output(classpath.project.relativePath(classpath.defaultOutputDir)))
        sourceFoldersCreator.populateForClasspath(entries, classpath)
        entries.addAll(getEntriesFromContainers(classpath.getContainers()))
        entries.addAll(getDependencies(classpath))
        return entries
    }

    private List getEntriesFromContainers(Set containers) {
        containers.collect { container ->
            new Container(container, true, null, [] as Set)
        }
    }

    private List getDependencies(EclipseClasspath classpath) {
        if (classpath.projectDependenciesOnly) {
            getModules(classpath)
        } else {
            getModules(classpath) + getLibraries(classpath) + classFoldersCreator.create(classpath)
        }
    }

    protected List getModules(EclipseClasspath classpath) {
        def result = []
        for (configuration in classpath.plusConfigurations) {
            result.addAll(createClasspathEntries(configuration, classpath.minusConfigurations,
                { it instanceof org.gradle.api.artifacts.ProjectDependency },
                { dependency -> new ProjectDependencyBuilder().build(dependency.dependencyProject, isExported(dependency, configuration, classpath)) }))
        }
        result
    }

    protected Set getLibraries(EclipseClasspath classpath) {
        def allResolvedDependencies = resolveDependencies(classpath.plusConfigurations, classpath.minusConfigurations)

        Set sourceDependencies = getResolvableDependenciesForAllResolvedDependencies(allResolvedDependencies) { dependency ->
            addSourceArtifact(dependency)
        }
        Map sourceFiles = classpath.downloadSources ? getFiles(classpath.project, sourceDependencies, "sources") : [:]

        Set javadocDependencies = getResolvableDependenciesForAllResolvedDependencies(allResolvedDependencies) { dependency ->
            addJavadocArtifact(dependency)
        }
        Map javadocFiles = classpath.downloadJavadoc ? getFiles(classpath.project, javadocDependencies, "javadoc") : [:]

        def moduleLibraries = new LinkedHashSet()
        for (configuration in classpath.plusConfigurations) {
            moduleLibraries.addAll(createClasspathEntries(configuration, classpath.minusConfigurations,
                { it instanceof ExternalDependency },
                { ExternalDependency dependency ->
                    configuration.files(dependency).collect { File binaryFile ->
                        File sourceFile = sourceFiles[binaryFile.name]
                        File javadocFile = javadocFiles[binaryFile.name]
                        createLibraryEntry(binaryFile, sourceFile, javadocFile, classpath.pathVariables, isExported(dependency, configuration, classpath))
                    }
                }))
            moduleLibraries.addAll(createClasspathEntries(configuration, classpath.minusConfigurations,
                { it instanceof SelfResolvingDependency && !(it instanceof org.gradle.api.artifacts.ProjectDependency)},
                { SelfResolvingDependency dependency ->
                    dependency.resolve().collect { File file ->
                        createLibraryEntry(file, null, null, classpath.pathVariables, isExported(dependency, configuration, classpath))
                    }
                }))
        }
        moduleLibraries
    }

    AbstractLibrary createLibraryEntry(File binary, File source, File javadoc, Map<String, File> pathVariables, boolean exported) {
        def usedVariableEntry = pathVariables.find { String name, File value -> binary.canonicalPath.startsWith(value.canonicalPath) }
        if (usedVariableEntry) {
            String name = usedVariableEntry.key
            String value = usedVariableEntry.value.canonicalPath
            String binaryPath = name + binary.canonicalPath.substring(value.length())
            String sourcePath = source ? name + source.canonicalPath.substring(value.length()) : null
            String javadocPath = javadoc ? name + javadoc.canonicalPath.substring(value.length()) : null
            return new Variable(binaryPath, exported, null, [] as Set, sourcePath, javadocPath)
        }
        new Library(binary.canonicalPath, exported, null, [] as Set, source ? source.canonicalPath : null, javadoc ? javadoc.canonicalPath : null)
    }

    private boolean isExported(Dependency dependency, Configuration configuration, EclipseClasspath classpath) {
        // a dependency is exported if it is introduced by an exported configuration
        for (Configuration superConfig : configuration.hierarchy) {
            if (classpath.nonExportedConfigurations.contains(superConfig)) {
                continue
            }
            if (superConfig.allDependencies.contains(dependency)) {
                return true
            }
        }
        false
    }

    protected List createClasspathEntries(Configuration configuration, Collection<Configuration> minusConfigurations, Closure depFilter, Closure entryGenerator) {
        def result = new LinkedHashSet()
        def deps = configuration.allDependencies.findAll(depFilter)
        for (minusConfiguration in minusConfigurations) {
            deps.removeAll(minusConfiguration.allDependencies.findAll(depFilter))
        }
        result.addAll(deps.collect(entryGenerator))
        result.flatten() as List
    }

    private Set<ResolvedDependency> resolveDependencies(Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        def result = new LinkedHashSet()
        for (configuration in plusConfigurations) {
            result.addAll(getAllDeps(configuration.resolvedConfiguration.getFirstLevelModuleDependencies({ it instanceof ExternalDependency } as Spec)))
        }
        for (minusConfiguration in minusConfigurations) {
            result.removeAll(getAllDeps(minusConfiguration.resolvedConfiguration.getFirstLevelModuleDependencies({ it instanceof ExternalDependency } as Spec)))
        }
        result
    }

    private getFiles(org.gradle.api.Project project, Set dependencies, String classifier) {
        return project.configurations.detachedConfiguration((dependencies as Dependency[])).files.inject([:]) { result, sourceFile ->
            String key = sourceFile.name.replace("-${classifier}.jar", '.jar')
            result[key] = sourceFile
            result
        }
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

    protected Set getAllDeps(Collection deps, Set allDeps = []) {
        deps.each { ResolvedDependency resolvedDependency ->
            def notSeenBefore = allDeps.add(resolvedDependency)
            if (notSeenBefore) { // defend against circular dependencies
                getAllDeps(resolvedDependency.children, allDeps)
            }
        }
        allDeps
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
}
