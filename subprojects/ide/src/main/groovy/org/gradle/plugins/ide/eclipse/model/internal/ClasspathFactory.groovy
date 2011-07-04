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

import org.gradle.plugins.ide.internal.IdeDependenciesExtractor
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor.IdeLocalFileDependency
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor.IdeProjectDependency
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor.IdeRepoFileDependency
import org.gradle.plugins.ide.eclipse.model.*

/**
 * @author Hans Dockter
 */
class ClasspathFactory {

    private final IdeDependenciesExtractor dependenciesExtractor = new IdeDependenciesExtractor()
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
            getProjects(classpath)
        } else {
            getProjects(classpath) + getLibraries(classpath) + classFoldersCreator.create(classpath)
        }
    }

    protected List getProjects(EclipseClasspath classpath) {
        return dependenciesExtractor.extractProjectDependencies(classpath.plusConfigurations, classpath.minusConfigurations)
                .collect { IdeProjectDependency it -> new ProjectDependencyBuilder().build(it.project, it.declaredConfiguration.name) }
    }

    protected Set getLibraries(EclipseClasspath classpath) {
        List libs = []

        dependenciesExtractor.extractRepoFileDependencies(
                classpath.project.configurations, classpath.plusConfigurations, classpath.minusConfigurations, classpath.downloadSources, classpath.downloadJavadoc)
        .each { IdeRepoFileDependency it ->
            libs << createLibraryEntry(it.file, it.sourceFile, it.javadocFile, it.declaredConfiguration.name, classpath.pathVariables)
        }

        dependenciesExtractor.extractLocalFileDependencies(classpath.plusConfigurations, classpath.minusConfigurations)
        .each { IdeLocalFileDependency it ->
            libs << createLibraryEntry(it.file, null, null, it.declaredConfiguration.name, classpath.pathVariables)
        }

        libs
    }

    private AbstractLibrary createLibraryEntry(File binary, File source, File javadoc, String declaredConfigurationName, Map<String, File> pathVariables) {
        def usedVariableEntry = pathVariables.find { String name, File value -> binary.canonicalPath.startsWith(value.canonicalPath) }
        def out
        if (usedVariableEntry) {
            String name = usedVariableEntry.key
            String value = usedVariableEntry.value.canonicalPath
            String binaryPath = name + binary.canonicalPath.substring(value.length())
            String sourcePath = source ? name + source.canonicalPath.substring(value.length()) : null
            String javadocPath = javadoc ? name + javadoc.canonicalPath.substring(value.length()) : null
            out = new Variable(binaryPath, true, null, [] as Set, sourcePath, javadocPath)
        } else {
            out = new Library(binary.canonicalPath, true, null, [] as Set, source ? source.canonicalPath : null, javadoc ? javadoc.canonicalPath : null)
        }
        out.declaredConfigurationName = declaredConfigurationName
        out
    }
}