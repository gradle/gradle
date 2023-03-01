/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.file.DirectoryTree
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskOutputs
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.Factory
import org.gradle.api.Buildable
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.function.Consumer

class UnpackingVisitorTest extends Specification {
    def context = Mock(Consumer)
    def resolver = Mock(PathToFileResolver)
    def patternSetFactory = Mock(Factory)
    def visitor = new UnpackingVisitor(context, resolver, TestFiles.taskDependencyFactory(), patternSetFactory)

    def "resolves null"() {
        when:
        visitor.add(null)

        then:
        0 * context._
    }

    def "resolves String"() {
        def file = new File('some-file')

        when:
        visitor.add('path')

        then:
        1 * resolver.resolve('path') >> file
        1 * context.accept(_) >> { FileCollectionInternal collection ->
            collection.files == [file] as Set
        }
        0 * context._
    }

    def "resolves File"() {
        def file = new File('some-file')
        def input = new File('path')

        when:
        visitor.add(input)

        then:
        1 * resolver.resolve(input) >> file
        1 * context.accept(_) >> { FileCollectionInternal collection ->
            collection.files == [file] as Set
        }
        0 * context._
    }

    def "resolves Path"() {
        def file = new File('some-file')
        def path = new File('path').path

        when:
        visitor.add(path)

        then:
        1 * resolver.resolve(path) >> file
        1 * context.accept(_) >> { FileCollectionInternal collection ->
            collection.files == [file] as Set
        }
        0 * context._
    }

    def "recursively resolves return value of a Closure"() {
        def fileCollection = Mock(FileCollectionInternal)

        when:
        visitor.add { fileCollection }

        then:
        1 * context.accept(fileCollection)
        0 * context._
    }

    def "resolves a Closure which returns null"() {
        when:
        visitor.add { null }

        then:
        0 * context._
    }

    def "resolves tasks outputs to its output files"() {
        def content = Mock(FileCollectionInternal)
        def outputs = Mock(TaskOutputs)

        when:
        visitor.add outputs

        then:
        1 * outputs.files >> content
        1 * context.accept(content)
        0 * context._
    }

    def "resolves task to its output files"() {
        def content = Mock(FileCollectionInternal)
        def outputs = Mock(TaskOutputs)
        def task = Mock(Task)

        when:
        visitor.add task

        then:
        1 * task.outputs >> outputs
        1 * outputs.files >> content
        1 * context.accept(content)
        0 * context._
    }

    def "recursively resolves return value of a Callable"() {
        def fileCollection = Mock(FileCollectionInternal)
        def callable = Mock(Callable)

        when:
        visitor.add(callable)

        then:
        1 * callable.call() >> fileCollection
        1 * context.accept(fileCollection)
        0 * context._
    }

    def "resolves a Callable which returns null"() {
        def callable = Mock(Callable)

        when:
        visitor.add(callable)

        then:
        1 * callable.call() >> null
        0 * context._
    }

    def "resolves elements of Iterable"() {
        def fileCollection = Mock(FileCollectionInternal)

        when:
        visitor.add([null, fileCollection])

        then:
        1 * context.accept(fileCollection)
        0 * context._
    }

    def "resolves elements of Buildable Iterable"() {
        def element = Mock(BuildableIterable)
        def task = Mock(Task)
        def file = new File('some-file')

        when:
        visitor.add(element)

        then:
        _ * element.iterator() >> ['file'].iterator()
        _ * element.buildDependencies >> Stub(TaskDependency)
        1 * resolver.resolve('file') >> file
        1 * context.accept(_) >> { FileCollectionInternal files ->
            files.buildDependencies.getDependencies(null) == [task] as Set
            files.files == [file] as Set
        }
        0 * context._
    }

    def "resolves elements of array"() {
        def fileCollection = Mock(FileCollectionInternal)

        when:
        visitor.add([null, fileCollection] as FileCollection[])

        then:
        1 * context.accept(fileCollection)
        0 * context._
    }

    def "resolves Provider"() {
        def provider = Mock(ProviderInternal)

        when:
        visitor.add(provider)

        then:
        1 * context.accept({ it instanceof ProviderBackedFileCollection })
        0 * context._
    }

    def "wraps DirectoryTree"() {
        def tree = Mock(TestDirectoryTree)

        when:
        visitor.add(tree)

        then:
        1 * context.accept({ it instanceof FileTreeAdapter })
        0 * _
    }

    interface TestDirectoryTree extends DirectoryTree, MinimalFileTree {}

    interface BuildableIterable extends Buildable, Iterable<String> {}
}
