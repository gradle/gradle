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
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.tasks.TaskOutputs
import org.gradle.internal.file.PathToFileResolver
import spock.lang.Specification

import java.util.concurrent.Callable

class UnpackingVisitorTest extends Specification {
    def context = Mock(FileCollectionResolveContext)
    def resolver = Mock(PathToFileResolver)
    def visitor = new UnpackingVisitor(context, resolver)

    def "resolves null"() {
        when:
        visitor.add(null)

        then:
        0 * context._
    }

    def "resolves String"() {
        when:
        visitor.add('path')

        then:
        1 * context.add('path', resolver)
        0 * context._
    }

    def "resolves File"() {
        def file = new File('path')

        when:
        visitor.add(file)

        then:
        1 * context.add(file, resolver)
        0 * context._
    }

    def "resolves Path"() {
        def path = new File('path').path

        when:
        visitor.add(path)

        then:
        1 * context.add(path, resolver)
        0 * context._
    }

    def "recursively resolves return value of a Closure"() {
        def fileCollection = Mock(FileCollection)

        when:
        visitor.add { fileCollection }

        then:
        1 * context.add(fileCollection)
        0 * context._
    }

    def "resolves a Closure which returns null"() {
        when:
        visitor.add { null }

        then:
        0 * context._
    }

    def "resolves tasks outputs to its output files"() {
        def content = Mock(FileCollection)
        def outputs = Mock(TaskOutputs)

        when:
        visitor.add outputs

        then:
        1 * outputs.files >> content
        1 * context.add(content)
        0 * context._
    }

    def "resolves task to its output files"() {
        def content = Mock(FileCollection)
        def outputs = Mock(TaskOutputs)
        def task = Mock(Task)

        when:
        visitor.add task

        then:
        1 * task.outputs >> outputs
        1 * outputs.files >> content
        1 * context.add(content)
        0 * context._
    }

    def "recursively resolves return value of a Callable"() {
        def fileCollection = Mock(FileCollection)
        def callable = Mock(Callable)

        when:
        visitor.add(callable)

        then:
        1 * callable.call() >> fileCollection
        1 * context.add(fileCollection)
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
        def fileCollection = Mock(FileCollection)


        when:
        visitor.add([null, fileCollection])

        then:
        1 * context.add(fileCollection)
        0 * context._
    }

    def "resolves elements of array"() {
        def fileCollection = Mock(FileCollection)

        when:
        visitor.add([null, fileCollection] as FileCollection[])

        then:
        1 * context.add(fileCollection)
        0 * context._
    }

    def "resolves Provider"() {
        def provider = Mock(ProviderInternal)

        when:
        visitor.add(provider)

        then:
        1 * context.maybeAdd(provider) >> true
        0 * context._
    }

    def "resolves value of Provider when Provider not handled by context"() {
        def provider = Mock(ProviderInternal)

        when:
        visitor.add(provider)

        then:
        1 * context.maybeAdd(provider) >> false
        1 * provider.get() >> "123"
        1 * context.add("123", resolver)
        0 * context._
    }

    def "fails when resolving Provider has no value"() {
        def provider = Mock(ProviderInternal)
        def failure = new IllegalStateException("No value")

        when:
        visitor.add(provider)

        then:
        def e = thrown(IllegalStateException)
        e == failure
        1 * context.maybeAdd(provider) >> false
        1 * provider.get() >> { throw failure }
        0 * context._
    }

    def "forwards DirectoryTree"() {
        def tree = Mock(DirectoryTree)

        when:
        visitor.add(tree)

        then:
        1 * context.add(tree)
        0 * _
    }
}
