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

import org.gradle.api.Task
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskOutputs
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

import java.util.concurrent.Callable

@UsesNativeServices
class DefaultFileCollectionResolveContextTest extends Specification {
    final FileResolver resolver = Mock()
    final DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext(resolver)

    def resolveAsFileCollectionReturnsEmptyListWhenContextIsEmpty() {
        expect:
        context.resolveAsFileCollections() == []
    }

    def resolveAsFileTreeReturnsEmptyListWhenContextIsEmpty() {
        expect:
        context.resolveAsFileTrees() == []
    }

    def resolveAsMinimalFileCollectionReturnsEmptyListWhenContextIsEmpty() {
        expect:
        context.resolveAsMinimalFileCollections() == []
    }

    def resolveAsFileCollectionWrapsAMinimalFileSet() {
        MinimalFileSet fileSet = Mock()

        when:
        context.add(fileSet)
        def result = context.resolveAsFileCollections()

        then:
        result.size() == 1
        result[0] instanceof FileCollectionAdapter
        result[0].fileCollection == fileSet
    }

    def resolveAsFileTreeConvertsTheElementsOfMinimalFileSet() {
        MinimalFileSet fileSet = Mock()
        File file = this.file('file1')
        File dir = directory('file2')
        File doesNotExist = nonExistent('file3')

        when:
        context.add(fileSet)
        def result = context.resolveAsFileTrees()

        then:
        result.size() == 2
        result[0] instanceof FileTreeAdapter
        result[0].tree instanceof SingletonFileTree
        result[0].tree.file == file
        result[1] instanceof FileTreeAdapter
        result[1].tree instanceof DirectoryFileTree
        result[1].tree.dir == dir
        1 * fileSet.files >> ([file, dir, doesNotExist] as LinkedHashSet)
    }

    def resolveAsMinimalFileCollectionReturnsMinimalFileSet() {
        MinimalFileSet fileSet = Mock()

        when:
        context.add(fileSet)
        def result = context.resolveAsMinimalFileCollections()

        then:
        result == [fileSet]
    }

    def resolveAsFileCollectionWrapsAMinimalFileTree() {
        MinimalFileTree fileTree = Mock()

        when:
        context.add(fileTree)
        def result = context.resolveAsFileCollections()

        then:
        result.size() == 1
        result[0] instanceof FileTreeAdapter
        result[0].tree == fileTree
    }

    def resolveAsFileTreesWrapsAMinimalFileTree() {
        MinimalFileTree fileTree = Mock()

        when:
        context.add(fileTree)
        def result = context.resolveAsFileTrees()

        then:
        result.size() == 1
        result[0] instanceof FileTreeAdapter
        result[0].tree == fileTree
    }

    def resolveAsMinimalFileCollectionWrapsAMinimalFileTree() {
        MinimalFileTree fileTree = Mock()

        when:
        context.add(fileTree)
        def result = context.resolveAsMinimalFileCollections()

        then:
        result == [fileTree]
    }

    def resolveAsFileCollectionsForAFileCollection() {
        FileCollectionInternal fileCollection = Mock()

        when:
        context.add(fileCollection)
        def result = context.resolveAsFileCollections()

        then:
        result == [fileCollection]
    }

    def resolveAsFileCollectionsDelegatesToACompositeFileCollection() {
        FileCollectionContainer composite = Mock()
        FileCollectionInternal contents = Mock()

        when:
        context.add(composite)
        def result = context.resolveAsFileCollections()

        then:
        result == [contents]
        1 * composite.visitContents(!null) >> { it[0].add(contents) }
    }

    def resolveAsFileTreesDelegatesToACompositeFileCollection() {
        FileCollectionContainer composite = Mock()
        FileTreeInternal contents = Mock()

        when:
        context.add(composite)
        def result = context.resolveAsFileTrees()

        then:
        result == [contents]
        1 * composite.visitContents(!null) >> { it[0].add(contents) }
    }

    def resolveAsMinimalFileCollectionsDelegatesToACompositeFileCollection() {
        FileCollectionContainer composite = Mock()
        MinimalFileCollection contents = Mock()

        when:
        context.add(composite)
        def result = context.resolveAsMinimalFileCollections()

        then:
        result == [contents]
        1 * composite.visitContents(!null) >> { it[0].add(contents) }
    }

    def resolvesCompositeFileCollectionsInDepthwiseOrder() {
        FileCollectionContainer parent1 = Mock()
        FileCollectionInternal child1 = Mock()
        FileCollectionContainer parent2 = Mock()
        FileCollectionInternal child2 = Mock()
        FileCollectionInternal child3 = Mock()

        when:
        context.add(parent1)
        context.add(child3)
        def result = context.resolveAsFileCollections()

        then:
        result == [child1, child2, child3]
        1 * parent1.visitContents(!null) >> { it[0].add(child1); it[0].add(parent2) }
        1 * parent2.visitContents(!null) >> { it[0].add(child2) }
    }

    def recursivelyResolvesReturnValueOfAClosure() {
        FileCollectionInternal content = Mock()

        when:
        context.add { content }
        def result = context.resolveAsFileCollections()

        then:
        result == [content]
    }

    def resolvesAClosureWhichReturnsNull() {
        when:
        context.add { null }
        def result = context.resolveAsFileCollections()

        then:
        result == []
    }

    def resolvesTasksOutputsWithEmptyFileCollection() {
        FileCollectionInternal content = Mock()
        TaskOutputs outputs = Mock()
        when:

        context.add outputs
        def result = context.resolveAsFileCollections()

        then:
        1 * outputs.files >> content
        result == [content]
    }

    def recursivelyResolvesReturnValueOfACallable() {
        FileCollectionInternal content = Mock()
        Callable<?> callable = Mock()

        when:
        context.add(callable)
        def result = context.resolveAsFileCollections()

        then:
        1 * callable.call() >> content
        result == [content]
    }

    def resolvesACallableWhichReturnsNull() {
        Callable<?> callable = Mock()

        when:
        context.add(callable)
        def result = context.resolveAsFileCollections()

        then:
        1 * callable.call() >> null
        result == []
    }

    def recursivelyResolvesElementsOfAnIterable() {
        FileCollectionInternal content = Mock()
        Iterable<Object> iterable = Mock()

        when:
        context.add(iterable)
        def result = context.resolveAsFileCollections()

        then:
        1 * iterable.iterator() >> [content].iterator()
        result == [content]
    }

    def recursivelyResolvesElementsAnArray() {
        FileCollectionInternal content = Mock()

        when:
        context.add([content] as Object[])
        def result = context.resolveAsFileCollections()

        then:
        result == [content]
    }

    def resolveAsFileCollectionsIgnoresATaskDependency() {
        TaskDependency dependency = Mock()

        when:
        context.add(dependency)
        def result = context.resolveAsFileCollections()

        then:
        result == []
    }

    def resolveAsFileTreesIgnoresATaskDependency() {
        TaskDependency dependency = Mock()

        when:
        context.add(dependency)
        def result = context.resolveAsFileTrees()

        then:
        result == []
    }

    def resolveAsMinimalFileCollectionsIgnoresATaskDependency() {
        TaskDependency dependency = Mock()

        when:
        context.add(dependency)
        def result = context.resolveAsMinimalFileCollections()

        then:
        result == []
    }

    def resolveAsFileCollectionsResolvesTaskToItsOutputFiles() {
        Task task = Mock()
        TaskOutputs outputs = Mock()
        FileCollectionInternal outputFiles = Mock()

        given:
        _ * task.outputs >> outputs
        _ * outputs.files >> outputFiles

        when:
        context.add(task)
        def result = context.resolveAsFileCollections()

        then:
        result == [outputFiles]
    }

    def resolveAsFileCollectionsUsesFileResolverToResolveOtherTypes() {
        File file1 = new File('a')
        File file2 = new File('b')

        when:
        context.add('a')
        context.add('b')
        def result = context.resolveAsFileCollections()

        then:
        result.size() == 2
        result[0] instanceof FileCollectionAdapter
        result[0].fileCollection instanceof ListBackedFileSet
        result[0].fileCollection.files as List == [file1]
        result[1] instanceof FileCollectionAdapter
        result[1].fileCollection instanceof ListBackedFileSet
        result[1].fileCollection.files as List == [file2]
        1 * resolver.resolve('a') >> file1
        1 * resolver.resolve('b') >> file2
    }

    def resolveAsFileTreeUsesFileResolverToResolveOtherTypes() {
        File file = file('a')

        when:
        context.add('a')
        def result = context.resolveAsFileTrees()

        then:
        result.size() == 1
        result[0] instanceof FileTreeAdapter
        result[0].tree instanceof SingletonFileTree
        result[0].tree.file == file
        1 * resolver.resolve('a') >> file
    }

    def resolveAsMinimalFileCollectionUsesFileResolverToResolveOtherTypes() {
        File file = file('a')

        when:
        context.add('a')
        def result = context.resolveAsMinimalFileCollections()

        then:
        result.size() == 1
        result[0] instanceof ListBackedFileSet
        result[0].files as List == [file]
        1 * resolver.resolve('a') >> file
    }

    def canPushContextWhichUsesADifferentFileResolverToConvertToFileCollections() {
        FileResolver fileResolver = Mock()
        File file = new File('a')

        when:
        context.push(fileResolver).add('a')
        def result = context.resolveAsFileCollections()

        then:
        result.size() == 1
        result[0] instanceof FileCollectionAdapter
        result[0].fileCollection instanceof ListBackedFileSet
        result[0].fileCollection.files as List == [file]
        1 * fileResolver.resolve('a') >> file
        0 * _._
    }

    def canPushContextWhichUsesADifferentFileResolverToConvertToFileTrees() {
        FileResolver fileResolver = Mock()
        File file = file('a')

        when:
        context.push(fileResolver).add('a')
        def result = context.resolveAsFileTrees()

        then:
        result.size() == 1
        result[0] instanceof FileTreeAdapter
        result[0].tree instanceof SingletonFileTree
        result[0].tree.file == file
        1 * fileResolver.resolve('a') >> file
    }

    def file(String name) {
        File f = Mock()
        _ * f.file >> true
        _ * f.exists() >> true
        _ * f.canonicalFile >> f
        f
    }

    def directory(String name) {
        File f = Mock()
        _ * f.directory >> true
        _ * f.exists() >> true
        _ * f.canonicalFile >> f
        f
    }

    def nonExistent(String name) {
        File f = Mock()
        _ * f.canonicalFile >> f
        f
    }
}
