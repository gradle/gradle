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


import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.file.PathToFileResolver
import spock.lang.Specification

class DefaultFileCollectionResolveContextTest extends Specification {
    def resolver = Mock(PathToFileResolver)
    def context = new DefaultFileCollectionResolveContext(TestFiles.patternSetFactory)

    def "resolve as FileCollection returns empty List when context is empty"() {
        expect:
        context.resolveAsFileCollections() == []
    }

    def "resolve as FileTree returns empty List when context is empty"() {
        expect:
        context.resolveAsFileTrees() == []
    }

    def "resolve as FileCollection wraps a MinimalFileSet"() {
        MinimalFileSet fileSet = Mock()

        when:
        context.add(fileSet)
        def result = context.resolveAsFileCollections()

        then:
        result.size() == 1
        result[0] instanceof FileCollectionAdapter
        result[0].fileSet == fileSet
    }

    def "resolve as FileTree converts the elements of MinimalFileSet"() {
        MinimalFileSet fileSet = Mock()
        File file = this.file('file1')
        File dir = directory('file2')
        File doesNotExist = nonExistent('file3')

        when:
        context.add(fileSet)
        def result = context.resolveAsFileTrees()

        then:
        result.size() == 3
        result[0] instanceof FileTreeAdapter
        result[0].tree instanceof DirectoryFileTree
        result[0].tree.dir == file
        result[1] instanceof FileTreeAdapter
        result[1].tree instanceof DirectoryFileTree
        result[1].tree.dir == dir
        result[2] instanceof FileTreeAdapter
        result[2].tree instanceof DirectoryFileTree
        result[2].tree.dir == doesNotExist
        1 * fileSet.files >> ([file, dir, doesNotExist] as LinkedHashSet)
    }

    def "resolve as FileCollection wraps a MinimalFileTree"() {
        MinimalFileTree fileTree = Mock()

        when:
        context.add(fileTree)
        def result = context.resolveAsFileCollections()

        then:
        result.size() == 1
        result[0] instanceof FileTreeAdapter
        result[0].tree == fileTree
    }

    def "resolve as FileTrees wraps a MinimalFileTree"() {
        MinimalFileTree fileTree = Mock()

        when:
        context.add(fileTree)
        def result = context.resolveAsFileTrees()

        then:
        result.size() == 1
        result[0] instanceof FileTreeAdapter
        result[0].tree == fileTree
    }

    def "resolve as FileCollections for a FileCollection"() {
        FileCollectionInternal fileCollection = Mock()

        when:
        context.add(fileCollection)
        def result = context.resolveAsFileCollections()

        then:
        result == [fileCollection]
    }

    def "resolve as FileCollections delegates to a CompositeFileCollection"() {
        FileCollectionContainer composite = Mock()
        FileCollectionInternal contents = Mock()

        when:
        context.add(composite)
        def result = context.resolveAsFileCollections()

        then:
        result == [contents]
        1 * composite.visitContents(!null) >> { it[0].add(contents) }
    }

    def "resolve as FileTrees delegates to a CompositeFileCollection"() {
        FileCollectionContainer composite = Mock()
        FileTreeInternal contents = Mock()

        when:
        context.add(composite)
        def result = context.resolveAsFileTrees()

        then:
        result == [contents]
        1 * composite.visitContents(!null) >> { it[0].add(contents) }
    }

    def "resolves CompositeFileCollections in depthwise order"() {
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

    def "resolve as FileCollections ignores a TaskDependency"() {
        TaskDependency dependency = Mock()

        when:
        context.add(dependency)
        def result = context.resolveAsFileCollections()

        then:
        result == []
    }

    def "resolve as FileTrees ignores a TaskDependency"() {
        TaskDependency dependency = Mock()

        when:
        context.add(dependency)
        def result = context.resolveAsFileTrees()

        then:
        result == []
    }

    def "resolve as FileCollections uses FileResolver to resolve other types"() {
        File file1 = new File('a')
        File file2 = new File('b')

        when:
        context.add('a', resolver)
        context.add('b', resolver)
        def result = context.resolveAsFileCollections()

        then:
        result.size() == 2
        result[0] instanceof FileCollectionAdapter
        result[0].fileSet instanceof ListBackedFileSet
        result[0].fileSet.files as List == [file1]
        result[1] instanceof FileCollectionAdapter
        result[1].fileSet instanceof ListBackedFileSet
        result[1].fileSet.files as List == [file2]
        1 * resolver.resolve('a') >> file1
        1 * resolver.resolve('b') >> file2
    }

    def "resolve as FileTree uses FileResolver to resolve other types"() {
        File file = file('a')

        when:
        context.add('a', resolver)
        def result = context.resolveAsFileTrees()

        then:
        result.size() == 1
        result[0] instanceof FileTreeAdapter
        result[0].tree instanceof DirectoryFileTree
        result[0].tree.dir == file
        1 * resolver.resolve('a') >> file
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
