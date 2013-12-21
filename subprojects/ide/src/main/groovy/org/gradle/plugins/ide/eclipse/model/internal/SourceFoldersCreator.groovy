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

import org.gradle.api.file.DirectoryTree
import org.gradle.api.tasks.SourceSet
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath
import org.gradle.plugins.ide.eclipse.model.SourceFolder

class SourceFoldersCreator {
    void populateForClasspath(List<ClasspathEntry> entries, EclipseClasspath classpath) {
        def provideRelativePath = { classpath.project.relativePath(it) }
        List regulars = getRegularSourceFolders(classpath.sourceSets, provideRelativePath)

        //externals are mapped to linked resources so we just need a name of the resource, without full path
        List trimmedExternals = getExternalSourceFolders(classpath.sourceSets, provideRelativePath).collect { SourceFolder folder ->
            folder.trimPath()
            folder
        }

        entries.addAll(regulars)
        entries.addAll(trimmedExternals)
    }

    /**
     * paths that navigate higher than project dir are not allowed in eclipse .classpath
     * regardless if they are absolute or relative
     *
     * @return source folders that live inside the project
     */
    List<SourceFolder> getRegularSourceFolders(Iterable<SourceSet> sourceSets, Closure provideRelativePath) {
        def sourceFolders = projectRelativeFolders(sourceSets, provideRelativePath)
        return sourceFolders.findAll { !it.path.contains('..') }
    }

    /**
     * see {@link #getRegularSourceFolders}
     *
     * @return source folders that live outside of the project
     */
    List<SourceFolder> getExternalSourceFolders(Iterable<SourceSet> sourceSets, Closure provideRelativePath) {
        def sourceFolders = projectRelativeFolders(sourceSets, provideRelativePath)
        return sourceFolders.findAll { it.path.contains('..') }
    }

    private List<SourceFolder> projectRelativeFolders(Iterable<SourceSet> sourceSets, Closure provideRelativePath) {
        def entries = []
        def sortedSourceSets = sortSourceSetsAsPerUsualConvention(sourceSets.collect{it})

        sortedSourceSets.each { SourceSet sourceSet ->
            def sortedSourceDirs = sortSourceDirsAsPerUsualConvention(sourceSet.allSource.srcDirTrees)

            sortedSourceDirs.each { tree ->
                def dir = tree.dir
                if (dir.isDirectory()) {
                    def folder = new SourceFolder(provideRelativePath(dir), null)
                    folder.dir = dir
                    folder.includes = tree.patterns.includes as List
                    folder.excludes = tree.patterns.excludes as List
                    entries.add(folder)
                }
            }
        }
        entries
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

    private List<DirectoryTree> sortSourceDirsAsPerUsualConvention(Collection<DirectoryTree> sourceDirs) {
        return sourceDirs.sort { sourceDir ->
            if (sourceDir.dir.path.endsWith("java")) { 0 }
            else if (sourceDir.dir.path.endsWith("resources")) { 2 }
            else { 1 }
        }
    }
}
