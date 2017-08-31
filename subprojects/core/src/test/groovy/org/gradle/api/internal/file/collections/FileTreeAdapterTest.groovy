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
package org.gradle.api.internal.file.collections

import org.gradle.api.Buildable
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.FileCollectionVisitor
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class FileTreeAdapterTest extends Specification {
    def toStringUsesDisplayName() {
        MinimalFileTree tree = Mock()
        _ * tree.displayName >> 'display name'

        FileTreeAdapter adapter = new FileTreeAdapter(tree)

        expect:
        adapter.toString() == 'display name'
    }

    def visitDelegatesToTargetTree() {
        MinimalFileTree tree = Mock()
        FileTreeAdapter adapter = new FileTreeAdapter(tree)
        FileVisitor visitor = Mock()

        when:
        adapter.visit(visitor)

        then:
        1 * tree.visit(visitor)
        0 * _._
    }

    def resolveAddsTargetTreeToContext() {
        MinimalFileTree tree = Mock()
        FileTreeAdapter adapter = new FileTreeAdapter(tree)
        FileCollectionResolveContext context = Mock()

        when:
        adapter.visitContents(context)

        then:
        1 * context.add(tree)
        0 * _._
    }

    def getAsFileTreesConvertsMirroringFileTreeByVisitingAllElementsAndReturningLocalMirror() {
        FileSystemMirroringFileTree tree = Mock()
        FileTreeAdapter adapter = new FileTreeAdapter(tree)
        DirectoryFileTreeFactory directoryFileTreeFactory = new DefaultDirectoryFileTreeFactory()
        DirectoryFileTree mirror = directoryFileTreeFactory.create(new File('a'))

        when:
        def result = adapter.asFileTrees

        then:
        result == [mirror]
        1 * tree.visit(!null) >> { it[0].visitFile({} as FileVisitDetails) }
        1 * tree.mirror >> mirror
        0 * _._
    }

    def getAsFileTreesConvertsEmptyMirroringTree() {
        FileSystemMirroringFileTree tree = Mock()
        FileTreeAdapter adapter = new FileTreeAdapter(tree)

        when:
        def result = adapter.asFileTrees

        then:
        result == []
        1 * tree.visit(!null)
        0 * _._
    }

    def getAsFileTreesConvertsLocalFileTree() {
        LocalFileTree tree = Mock()
        DirectoryFileTree contents = Mock()
        FileTreeAdapter adapter = new FileTreeAdapter(tree)

        when:
        def result = adapter.asFileTrees

        then:
        result == [contents]
        1 * tree.localContents >> [contents]
        0 * _._
    }

    def getBuildDependenciesDelegatesToTargetTreeWhenItImplementsBuildable() {
        TestFileTree tree = Mock()
        TaskDependency expectedDependency = Mock()
        FileTreeAdapter adapter = new FileTreeAdapter(tree)

        when:
        def dependencies = adapter.buildDependencies

        then:
        dependencies == expectedDependency
        1 * tree.buildDependencies >> expectedDependency
    }

    def matchingDelegatesToTargetTreeWhenItImplementsPatternFilterableFileTree() {
        PatternFilterableFileTree tree = Mock()
        MinimalFileTree filtered = Mock()
        PatternFilterable filter = Mock()
        FileTreeAdapter adapter = new FileTreeAdapter(tree)

        when:
        def filteredAdapter = adapter.matching(filter)

        then:
        filteredAdapter instanceof FileTreeAdapter
        filteredAdapter.tree == filtered
        1 * tree.filter(filter) >> filtered
    }

    def containsDelegatesToTargetTreeWhenItImplementsRandomAccessFileCollection() {
        TestFileTree tree = Mock()
        File f = new File('a')
        FileTreeAdapter adapter = new FileTreeAdapter(tree)

        when:
        def result = adapter.contains(f)

        then:
        result
        1 * tree.contains(f) >> true
    }

    def visitsBackingDirectoryTree() {
        def visitor = Mock(FileCollectionVisitor)
        def directoryFileTreeFactory = new DefaultDirectoryFileTreeFactory()
        def tree = directoryFileTreeFactory.create(new File("dir"))
        def adapter = new FileTreeAdapter(tree)

        when:
        adapter.visitRootElements(visitor)

        then:
        1 * visitor.visitDirectoryTree(tree)
        0 * visitor._
    }

    def visitsSelfWhenBackingTreeIsNotDirectoryTree() {
        def visitor = Mock(FileCollectionVisitor)
        def tree = Mock(MinimalFileTree)
        def adapter = new FileTreeAdapter(tree)

        when:
        adapter.visitRootElements(visitor)

        then:
        1 * visitor.visitTree(adapter)
        0 * visitor._
    }
}

interface TestFileTree extends MinimalFileTree, Buildable, RandomAccessFileCollection {
}
