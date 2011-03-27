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
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.SourceSet
import org.gradle.plugins.ide.eclipse.EclipseClasspath
import org.gradle.api.artifacts.*
import org.gradle.plugins.ide.eclipse.model.*

/**
 * @author Hans Dockter
 */
class ClasspathFactory {
    void configure(EclipseClasspath eclipseClasspath, Classpath classpath) {
        def entries = []
        entries.add(new Output(eclipseClasspath.project.relativePath(eclipseClasspath.defaultOutputDir)))
        entries.addAll(getEntriesFromSourceSets(eclipseClasspath.sourceSets, eclipseClasspath.project))
        entries.addAll(getEntriesFromContainers(eclipseClasspath.getContainers()))
        entries.addAll(getEntriesFromConfigurations(eclipseClasspath))

        classpath.configure(entries)
    }

    private List<SourceFolder> getEntriesFromSourceSets(Iterable<SourceSet> sourceSets, org.gradle.api.Project project) {
        List entries = []
        def sortedSourceSets = sortSourceSetsAsPerUsualConvention(sourceSets.collect{it})

        sortedSourceSets.each { SourceSet sourceSet ->
            def sourceDirSets = sourceSet.allSource.sourceTrees
            def sourceDirs = sourceDirSets.collect { it.srcDirs }.flatten()
            def sortedSourceDirs = sortSourceDirsAsPerUsualConvention(sourceDirs)

            sortedSourceDirs.each { dir ->
                if (dir.isDirectory()) {
                    def sourceDirSet = sourceDirSets.find { it.srcDirs.contains(dir) }
                    entries.add(new SourceFolder(
                            project.relativePath(dir),
                            null,
                            [] as Set,
                            null,
                            sourceDirSet.includes as List,
                            sourceDirSet.excludes as List))
                }
            }
        }
        entries
    }

    private List getEntriesFromContainers(Set containers) {
        containers.collect { container ->
            new Container(container, true, null, [] as Set)
        }
    }

    private List getEntriesFromConfigurations(EclipseClasspath eclipseClasspath) {
        getModules(eclipseClasspath) + getLibraries(eclipseClasspath)
    }

    protected List getModules(EclipseClasspath eclipseClasspath) {
        return getDependencies(eclipseClasspath.plusConfigurations, eclipseClasspath.minusConfigurations, { it instanceof org.gradle.api.artifacts.ProjectDependency }).collect { projectDependency ->
            projectDependency.dependencyProject
        }.collect { dependencyProject ->
            new ProjectDependencyBuilder().build(dependencyProject)
        }
    }

    protected Set getLibraries(EclipseClasspath eclipseClasspath) {
        Set declaredDependencies = getDependencies(eclipseClasspath.plusConfigurations, eclipseClasspath.minusConfigurations,
                { it instanceof ExternalDependency })

        ResolvedConfiguration resolvedConfiguration = eclipseClasspath.project.configurations.
                detachedConfiguration((declaredDependencies as Dependency[])).resolvedConfiguration
        def allResolvedDependencies = getAllDeps(resolvedConfiguration.firstLevelModuleDependencies)

        Set sourceDependencies = getResolvableDependenciesForAllResolvedDependencies(allResolvedDependencies) { dependency ->
            addSourceArtifact(dependency)
        }
        Map sourceFiles = eclipseClasspath.downloadSources ? getFiles(eclipseClasspath.project, sourceDependencies, "sources") : [:]

        Set javadocDependencies = getResolvableDependenciesForAllResolvedDependencies(allResolvedDependencies) { dependency ->
            addJavadocArtifact(dependency)
        }
        Map javadocFiles = eclipseClasspath.downloadJavadoc ? getFiles(eclipseClasspath.project, javadocDependencies, "javadoc") : [:]

        List moduleLibraries = resolvedConfiguration.getFiles(Specs.SATISFIES_ALL).collect { File binaryFile ->
            File sourceFile = sourceFiles[binaryFile.name]
            File javadocFile = javadocFiles[binaryFile.name]
            createLibraryEntry(binaryFile, sourceFile, javadocFile, eclipseClasspath.variables)
        }
        moduleLibraries.addAll(getSelfResolvingFiles(getDependencies(eclipseClasspath.plusConfigurations, eclipseClasspath.minusConfigurations,
                { it instanceof SelfResolvingDependency && !(it instanceof org.gradle.api.artifacts.ProjectDependency)}), eclipseClasspath.variables))
        moduleLibraries
    }

    private getSelfResolvingFiles(Collection dependencies, Map<String, File> variables) {
        dependencies.collect { SelfResolvingDependency dependency ->
            dependency.resolve().collect { File file ->
                createLibraryEntry(file, null, null, variables)
            }
        }.flatten()
    }

    AbstractLibrary createLibraryEntry(File binary, File source, File javadoc, Map<String, File> variables) {
        def usedVariableEntry = variables.find { String name, File value -> binary.canonicalPath.startsWith(value.canonicalPath) }
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

    private Set getDependencies(Set plusConfigurations, Set minusConfigurations, Closure filter) {
        Set declaredDependencies = new LinkedHashSet()
        plusConfigurations.each { configuration ->
            declaredDependencies.addAll(configuration.allDependencies.findAll(filter))
        }
        minusConfigurations.each { configuration ->
            configuration.allDependencies.findAll(filter).each { minusDep ->
                declaredDependencies.remove(minusDep)
            }
        }
        return declaredDependencies
    }

    private getFiles(def project, Set dependencies, String classifier) {
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

    private List<SourceSet> sortSourceSetsAsPerUsualConvention(Collection<SourceSet> sourceSets) {
        return sourceSets.sort { sourceSet ->
            switch(sourceSet.name) {
                case SourceSet.MAIN_SOURCE_SET_NAME: return 0
                case SourceSet.TEST_SOURCE_SET_NAME: return 1
                default: return 2
            }
        }
    }

    private List<File> sortSourceDirsAsPerUsualConvention(Collection<File> sourceDirs) {
        return sourceDirs.sort { sourceDir ->
            if (sourceDir.path.endsWith("java")) { 0 }
            else if (sourceDir.path.endsWith("resources")) { 2 }
            else { 1 }
        }
    }

    protected Set getAllDeps(Set deps, Set allDeps = []) {
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
