/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.util.PatternSet
import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants
import org.gradle.plugins.ide.eclipse.model.SourceFolder
import spock.lang.Specification
import spock.lang.TempDir

class SourceFoldersCreatorTest extends Specification {

    @TempDir
    File tempFolder;

    SourceSet sourceSet;
    SourceDirectorySet java
    SourceDirectorySet resources
    SourceDirectorySet allSource

    DirectoryTree javaTree;
    DirectoryTree resourcesTree;
    File projectRootFolder;
    File defaultOutputFolder;
    File baseSourceOutputFolder;

    def setup() {
        sourceSet = Mock()
        java = Mock()
        resources = Mock()
        allSource = Mock()
        _ * sourceSet.name >> 'source_set'
        _ * sourceSet.allSource >> allSource
        _ * sourceSet.allJava >> java
        _ * sourceSet.resources >> resources
        projectRootFolder = new File(tempFolder, "project-root").tap { mkdirs() }
        defaultOutputFolder = new File(projectRootFolder, EclipsePluginConstants.DEFAULT_PROJECT_OUTPUT_PATH)
        baseSourceOutputFolder = new File(projectRootFolder, "bin")
    }

    def "applies excludes/includes for src folders"() {
        given:
        def patterns = ["**/*.xml"]
        javaTree = dirTree("java", [], patterns)
        resourcesTree = dirTree("resources", patterns, [])
        when:
        def folders = regularSourceFolders()
        then:
        folders.find { it.dir.path.endsWith("java") }.excludes == []
        folders.find { it.dir.path.endsWith("java") }.includes == patterns
        folders.find { it.dir.path.endsWith("resources") }.excludes == patterns
        folders.find { it.dir.path.endsWith("resources") }.includes == []
    }

    def "ignores excludes patterns when specified for one of shared resources/sources folders"() {
        given:
        def patterns = ["**/*.java"]
        javaTree = dirTree("shared", [], [])
        resourcesTree = dirTree("shared", patterns, [])
        when:
        def folders = regularSourceFolders()
        then:
        folders.find { it.dir.path.endsWith("shared") }.excludes == []
    }

    def "applies excludes when specified on resources and on sources"() {
        given:
        def patterns = ["**/*.xml"]
        javaTree = dirTree("shared", patterns, [])
        resourcesTree = dirTree("shared", patterns, [])
        when:
        def folders = regularSourceFolders()
        then:
        folders.find { it.dir.path.endsWith("shared") }.excludes == patterns
        folders.find { it.dir.path.endsWith("shared") }.includes == []
    }

    def "applies no includes when one source type has no includes declared"() {
        given:
        javaTree = dirTree("shared", [], [])
        resourcesTree = dirTree("shared", [], ["**/*.properties"])
        when:
        def folders = regularSourceFolders()
        then:
        folders.find { it.dir.path.endsWith("shared") }.excludes == []
        folders.find { it.dir.path.endsWith("shared") }.includes == []
    }

    def "applies includes when all source types have declared includes"() {
        given:
        javaTree = dirTree("shared", [], ["**/*.java", "**/*.xml"])
        resourcesTree = dirTree("shared", [], ["**/*.properties", "**/*.xml"])
        when:
        def folders = regularSourceFolders()
        then:
        folders.find { it.dir.path.endsWith("shared") }.excludes == []
        folders.find { it.dir.path.endsWith("shared") }.includes == ["**/*.properties", "**/*.xml", "**/*.java"]
    }

    def "dedups external sourcefolder names"() {
        when:
        def folders = externalSourceFolders("../parent1/sibling1/src", "../parent2/sibling1/src", "../sibling2/src", "../parent1/sibling1/sib-src")
        then:
        folders.collect { it.path } ==  ["sibling1-src", "parent2-sibling1-src", "sibling2-src", "sib-src"]
    }

    private List<SourceFolder> regularSourceFolders() {
        _ * java.srcDirs >> [javaTree.dir]
        _ * java.excludes >> javaTree.patterns.excludes
        _ * java.includes >> javaTree.patterns.includes
        _ * java.srcDirTrees >> [javaTree]
        _ * resources.srcDirs >> [resourcesTree.dir]
        _ * resources.excludes >> resourcesTree.patterns.excludes
        _ * resources.includes >> resourcesTree.patterns.includes
        _ * resources.srcDirTrees >> [resourcesTree]
        _ * allSource.getSrcDirTrees() >> [javaTree, resourcesTree]
        return new SourceFoldersCreator().configureProjectRelativeFolders([sourceSet], [], { File file -> file.path },
            defaultOutputFolder, baseSourceOutputFolder.absolutePath)
    }

    private List<SourceFolder> externalSourceFolders(String... paths) {
        javaTree = dirTree("src", [], [])
        resourcesTree = dirTree("resources", [], [])

        def allExtTrees = paths.collect { path -> dirTree(path, [], []) }

        _ * java.srcDirs >> allExtTrees.collect { it.dir } + javaTree.dir
        _ * java.srcDirTrees >> allExtTrees + javaTree
        _ * resources.srcDirs >> [resourcesTree.dir]
        _ * resources.srcDirTrees >> [resourcesTree]
        _ * allSource.getSrcDirTrees() >> allExtTrees + [javaTree, resourcesTree]
        return new SourceFoldersCreator().getBasicExternalSourceFolders([sourceSet], { File file -> file.path }, defaultOutputFolder)
    }

    private def dirTree(String sourceDirName, List excludes, List includes) {
        File dir = new File(projectRootFolder, sourceDirName);
        dir.mkdirs()
        new TestDirectoryTree(dir: dir,
            patterns: new PatternSet().setExcludes(excludes).setIncludes(includes));
    }

    private static class TestDirectoryTree implements DirectoryTree {
        File dir
        PatternSet patterns
    }
}
