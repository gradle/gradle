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
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class FileTreeAdapterTest extends Specification {
    private FileTreeAdapter newFileTreeAdapter(MinimalFileTree tree) {
        return new FileTreeAdapter(tree, TestFiles.taskDependencyFactory(), TestFiles.patternSetFactory)
    }

    def toStringUsesDisplayName() {
        MinimalFileTree tree = Mock()
        _ * tree.displayName >> 'display name'

        FileTreeAdapter adapter = newFileTreeAdapter(tree)

        expect:
        adapter.toString() == 'display name'
    }

    def visitDelegatesToTargetTree() {
        MinimalFileTree tree = Mock()
        FileTreeAdapter adapter = newFileTreeAdapter(tree)
        FileVisitor visitor = Mock()

        when:
        adapter.visit(visitor)

        then:
        1 * tree.visit(visitor)
        0 * _._
    }

    def visitDependenciesDelegatesToTargetTreeWhenItImplementsBuildable() {
        TestFileTree tree = Mock()
        TaskDependencyResolveContext context = Mock()
        FileTreeAdapter adapter = newFileTreeAdapter(tree)

        when:
        adapter.visitDependencies(context)

        then:
        1 * context.add(tree)
    }

    def visitDependenciesDoesNotDelegateToTargetTreeWhenItDoesNotImplementBuildable() {
        MinimalFileTree tree = Mock()
        TaskDependencyResolveContext context = Mock()
        FileTreeAdapter adapter = newFileTreeAdapter(tree)

        when:
        adapter.visitDependencies(context)

        then:
        0 * context._
    }

    def matchingDelegatesToTargetTreeWhenItImplementsPatternFilterableFileTree() {
        PatternFilterableFileTree tree = Mock()
        MinimalFileTree filtered = Mock()
        PatternFilterable filter = Mock()
        FileTreeAdapter adapter = newFileTreeAdapter(tree)

        when:
        def filteredAdapter = adapter.matching(filter)

        then:
        filteredAdapter instanceof FileTreeAdapter
        filteredAdapter.tree == filtered
        1 * tree.filter(filter) >> filtered
    }

    def matchingWrapsTargetTreeWhenItDoesNotImplementPatternFilterableFileTree() {
        FileSystemMirroringFileTree tree = Mock()
        PatternSet filter = Mock()
        FileTreeAdapter adapter = newFileTreeAdapter(tree)

        when:
        def filteredAdapter = adapter.matching(filter)

        then:
        filteredAdapter instanceof FileTreeAdapter
        filteredAdapter.tree instanceof FilteredMinimalFileTree
        filteredAdapter.tree.tree == tree
        filteredAdapter.tree.patterns == filter
    }

    def containsDelegatesToTargetTreeWhenItImplementsRandomAccessFileCollection() {
        TestFileTree tree = Mock()
        File f = new File('a')
        FileTreeAdapter adapter = newFileTreeAdapter(tree)

        when:
        def result = adapter.contains(f)

        then:
        result
        1 * tree.contains(f) >> true
    }

    interface TestFileTree extends MinimalFileTree, Buildable, RandomAccessFileCollection {
    }
}
