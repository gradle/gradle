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
        return getDependencies(classpath.plusConfigurations, classpath.minusConfigurations, { it instanceof org.gradle.api.artifacts.ProjectDependency })
                .collect { projectDependency -> new ProjectDependencyBuilder().build(projectDependency.dependencyProject) }
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

        List moduleLibraries = resolveFiles(classpath.plusConfigurations, classpath.minusConfigurations).collect { File binaryFile ->
            File sourceFile = sourceFiles[binaryFile.name]
            File javadocFile = javadocFiles[binaryFile.name]
            createLibraryEntry(binaryFile, sourceFile, javadocFile, classpath.pathVariables)
        }
        moduleLibraries.addAll(getSelfResolvingFiles(getDependencies(classpath.plusConfigurations, classpath.minusConfigurations,
                { it instanceof SelfResolvingDependency && !(it instanceof org.gradle.api.artifacts.ProjectDependency)}), classpath.pathVariables))
        moduleLibraries
    }

    private getSelfResolvingFiles(Collection dependencies, Map<String, File> pathVariables) {
        dependencies.collect { SelfResolvingDependency dependency ->
            dependency.resolve().collect { File file ->
                createLibraryEntry(file, null, null, pathVariables)
            }
        }.flatten()
    }

    AbstractLibrary createLibraryEntry(File binary, File source, File javadoc, Map<String, File> pathVariables) {
        def usedVariableEntry = pathVariables.find { String name, File value -> binary.canonicalPath.startsWith(value.canonicalPath) }
        if (usedVariableEntry) {
            String name = usedVariableEntry.key
            String value = usedVariableEntry.value.canonicalPath
            String binaryPath = name + binary.canonicalPath.substring(value.length())
            String sourcePath = source ? name + source.canonicalPath.substring(value.length()) : null
            String javadocPath = javadoc ? name + javadoc.canonicalPath.substring(value.length()) : null
            return new Variable(binaryPath, true, null, [] as Set, sourcePath, javadocPath)
        }
        new Library(binary.canonicalPath, true, null, [] as Set, source ? source.canonicalPath : null, javadoc ? javadoc.canonicalPath : null)
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
