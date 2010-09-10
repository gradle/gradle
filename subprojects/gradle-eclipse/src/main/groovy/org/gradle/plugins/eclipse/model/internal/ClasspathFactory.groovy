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
package org.gradle.plugins.eclipse.model.internal

import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.CachingDirectedGraphWalker
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.DirectedGraph 
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency 
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.SourceSet
import org.gradle.api.artifacts.*
import org.gradle.plugins.eclipse.model.*
import org.gradle.plugins.eclipse.EclipseClasspath

/**
 * @author Hans Dockter
 */
class ClasspathFactory {
    private final CachingDirectedGraphWalker<ResolvedDependency, ResolvedDependency> walker = 
        new CachingDirectedGraphWalker<ResolvedDependency, ResolvedDependency>(new ResolvedDependencyGraph())
    
    Classpath createClasspath(EclipseClasspath eclipseClasspath) {
        File inputFile = eclipseClasspath.inputFile
        FileReader inputReader = inputFile != null && inputFile.exists() ? new FileReader(inputFile) : null
        List entries = getEntriesFromSourceSets(eclipseClasspath.sourceSets, eclipseClasspath.project)
        entries.addAll(getEntriesFromContainers(eclipseClasspath.getContainers()))
        entries.addAll(getEntriesFromConfigurations(eclipseClasspath))
        return new Classpath(eclipseClasspath, entries, inputReader)
    }

    List getEntriesFromSourceSets(def sourceSets, def project) {
        List entries = []
        sourceSets.each { SourceSet sourceSet ->
            sourceSet.allSource.sourceTrees.each { SourceDirectorySet sourceDirectorySet ->
                sourceDirectorySet.srcDirs.each { dir ->
                    if (dir.isDirectory()) {
                        entries.add(new SourceFolder(
                                project.relativePath(dir),
                                null,
                                [] as Set,
                                project.relativePath(sourceSet.classesDir),
                                sourceDirectorySet.getIncludes() as List,
                                sourceDirectorySet.getExcludes() as List))
                    }
                }
            }
        }
        entries
    }

    List getEntriesFromContainers(Set containers) {
        containers.collect { container ->
            new Container(container, true, null, [] as Set)
        }
    }

    List getEntriesFromConfigurations(EclipseClasspath eclipseClasspath) {
        getModules(eclipseClasspath) + getLibraries(eclipseClasspath) 
    }

    protected List getModules(EclipseClasspath eclipseClasspath) {
        return getDependencies(eclipseClasspath.plusConfigurations, eclipseClasspath.minusConfigurations, { it instanceof org.gradle.api.artifacts.ProjectDependency }).collect { projectDependency ->
            projectDependency.dependencyProject
        }.collect { dependencyProject ->
            new org.gradle.plugins.eclipse.model.ProjectDependency('/' + dependencyProject.name, true, null, [] as Set)
        }
    }

    protected Set getLibraries(EclipseClasspath eclipseClasspath) {
        Set declaredDependencies = getDependencies(eclipseClasspath.plusConfigurations, eclipseClasspath.minusConfigurations,
                { it instanceof ExternalDependency})

        ResolvedConfiguration resolvedConfiguration = eclipseClasspath.project.configurations.
                detachedConfiguration((declaredDependencies as Dependency[])).resolvedConfiguration
        def allResolvedDependencies = getAllDeps(resolvedConfiguration.firstLevelModuleDependencies)

        Map sourceFiles = [:]
        if (eclipseClasspath.downloadSources) {
            Set sourceDependencies = getResolvableDependenciesForAllResolvedDependencies(allResolvedDependencies) { dependency ->
                addSourceArtifact(dependency)
            }
            sourceFiles += getFiles(eclipseClasspath.project, sourceDependencies, "sources")
        }

        Map javadocFiles = [:]
        if (eclipseClasspath.downloadJavadoc) {
            Set javadocDependencies = getResolvableDependenciesForAllResolvedDependencies(allResolvedDependencies) { dependency ->
                addJavadocArtifact(dependency)
            }
            javadocFiles += getFiles(eclipseClasspath.project, javadocDependencies, "javadoc")
        }

        List moduleLibraries = resolvedConfiguration.getFiles(Specs.SATISFIES_ALL).collect { File binaryFile ->
            File sourceFile = sourceFiles[binaryFile.name]
            File javadocFile = javadocFiles[binaryFile.name]
            createLibraryEntry(binaryFile, sourceFile, javadocFile, eclipseClasspath.variables)
        }
        moduleLibraries.addAll(getSelfResolvingFiles(getDependencies(eclipseClasspath.plusConfigurations, eclipseClasspath.minusConfigurations,
                { it instanceof SelfResolvingDependency && !(it instanceof org.gradle.api.artifacts.ProjectDependency)}), eclipseClasspath.variables))
        moduleLibraries
    }

    private def getSelfResolvingFiles(Collection dependencies, Map variables) {
        dependencies.inject([] as LinkedHashSet) { result, SelfResolvingDependency selfResolvingDependency ->
            result.addAll(selfResolvingDependency.resolve().collect { File file ->
                createLibraryEntry(file, null, null, variables)
            })
            result
        }
    }

    AbstractLibrary createLibraryEntry(File binary, File source, File javadoc, Map variables) {
        def usedVariableEntry = variables.find { name, value -> binary.canonicalPath.startsWith(value) }
        if (usedVariableEntry) {
            String name = usedVariableEntry.key
            String value = usedVariableEntry.value
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
            declaredDependencies.addAll(configuration.getAllDependencies().findAll(filter))
        }
        minusConfigurations.each { configuration ->
            configuration.getAllDependencies().findAll(filter).each { minusDep ->
                declaredDependencies.remove(minusDep)
            }
        }
        return declaredDependencies
    }

    private def getFiles(def project, Set dependencies, String classifier) {
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

    protected Set getAllDeps(Set deps) {
        walker.add(deps)
        walker.findValues()
    }

    protected def addSourceArtifact(DefaultExternalModuleDependency dependency) {
        dependency.artifact { artifact ->
            artifact.name = dependency.name
            artifact.type = 'source'
            artifact.extension = 'jar'
            artifact.classifier = 'sources'
        }
    }

    protected def addJavadocArtifact(DefaultExternalModuleDependency dependency) {
        dependency.artifact { artifact ->
            artifact.name = dependency.name
            artifact.type = 'javadoc'
            artifact.extension = 'jar'
            artifact.classifier = 'javadoc'
        }
    }
}

private class ResolvedDependencyGraph implements DirectedGraph<ResolvedDependency, ResolvedDependency> {
    public void getNodeValues(ResolvedDependency node, Collection<ResolvedArtifact> values,
                              Collection<ResolvedDependency> connectedNodes) {
        values.addAll(node)
        connectedNodes.addAll(node.getChildren())
    }
}
