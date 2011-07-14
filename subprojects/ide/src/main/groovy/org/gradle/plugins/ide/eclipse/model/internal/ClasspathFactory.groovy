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

    interface ClasspathEntryBuilder {
        void update(List<ClasspathEntry> entries, EclipseClasspath eclipseClasspath)
    }

    private final ClasspathEntryBuilder outputCreator = new ClasspathEntryBuilder() {
        void update(List<ClasspathEntry> entries, EclipseClasspath eclipseClasspath) {
            entries.add(new Output(eclipseClasspath.project.relativePath(eclipseClasspath.defaultOutputDir)))
        }
    }

    private final ClasspathEntryBuilder containersCreator = new ClasspathEntryBuilder() {
        void update(List<ClasspathEntry> entries, EclipseClasspath eclipseClasspath) {
            eclipseClasspath.containers.each { container ->
                entries << new Container(container, true, null, [] as Set)
            }
        }
    }

    private final ClasspathEntryBuilder projectDependenciesCreator = new ClasspathEntryBuilder() {
        void update(List<ClasspathEntry> entries, EclipseClasspath eclipseClasspath) {
            entries.addAll(dependenciesExtractor.extractProjectDependencies(eclipseClasspath.plusConfigurations, eclipseClasspath.minusConfigurations)
                .collect { IdeProjectDependency it -> new ProjectDependencyBuilder().build(it.project, it.declaredConfiguration.name) })
        }
    }

    private final ClasspathEntryBuilder librariesCreator = new ClasspathEntryBuilder() {
        void update(List<ClasspathEntry> entries, EclipseClasspath classpath) {
            dependenciesExtractor.extractRepoFileDependencies(
                    classpath.project.configurations, classpath.plusConfigurations, classpath.minusConfigurations, classpath.downloadSources, classpath.downloadJavadoc)
            .each { IdeRepoFileDependency it ->
                entries << createLibraryEntry(it.file, it.sourceFile, it.javadocFile, it.declaredConfiguration.name, classpath.pathVariables)
            }

            dependenciesExtractor.extractLocalFileDependencies(classpath.plusConfigurations, classpath.minusConfigurations)
            .each { IdeLocalFileDependency it ->
                entries << createLibraryEntry(it.file, null, null, it.declaredConfiguration.name, classpath.pathVariables)
            }
        }
    }

    private final sourceFoldersCreator = new SourceFoldersCreator()
    private final IdeDependenciesExtractor dependenciesExtractor = new IdeDependenciesExtractor()
    private final classFoldersCreator = new ClassFoldersCreator()

    List<ClasspathEntry> createEntries(EclipseClasspath classpath) {
        def entries = []
        outputCreator.update(entries, classpath)
        sourceFoldersCreator.populateForClasspath(entries, classpath)
        containersCreator.update(entries, classpath)
        if (classpath.projectDependenciesOnly) {
            projectDependenciesCreator.update(entries, classpath)
        } else {
            projectDependenciesCreator.update(entries, classpath)
            librariesCreator.update(entries, classpath)
            entries.addAll(classFoldersCreator.create(classpath))
        }
        return entries
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
            out = new Library(binary.absolutePath, true, null, [] as Set, source ? source.absolutePath : null, javadoc ? javadoc.absolutePath : null)
        }
        out.declaredConfigurationName = declaredConfigurationName
        out
    }
}
