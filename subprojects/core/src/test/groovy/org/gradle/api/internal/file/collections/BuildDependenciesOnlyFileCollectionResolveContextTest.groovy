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
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskOutputs
import spock.lang.Specification

import java.util.concurrent.Callable

class BuildDependenciesOnlyFileCollectionResolveContextTest extends Specification {
    def taskContext = Mock(TaskDependencyResolveContext)
    def context = new BuildDependenciesOnlyFileCollectionResolveContext(taskContext)

    def ignoresAMinimalFileCollection() {
        def fileCollection = Mock(MinimalFileCollection)

        when:
        context.add(fileCollection)

        then:
        0 * taskContext._
    }

    def queuesAMinimalFileCollectionWhichImplementsBuildable() {
        def fileCollection = Mock(TestFileSet)

        when:
        context.add(fileCollection)

        then:
        1 * taskContext.add(fileCollection)
        0 * taskContext._
    }

    def queuesAFileCollection() {
        def fileCollection = Mock(FileCollection)

        when:
        context.add(fileCollection)

        then:
        1 * taskContext.add(fileCollection)
        0 * taskContext._
    }

    def queuesATask() {
        def task = Mock(Task)

        when:
        context.add(task)

        then:
        1 * taskContext.add(task)
        0 * taskContext._
    }

    def queuesATaskOutputs() {
        def outputs = Mock(TaskOutputs)
        def files = Mock(FileCollection)

        given:
        outputs.files >> files

        when:
        context.add(outputs)

        then:
        1 * taskContext.add(files)
        0 * taskContext._
    }

    def invokesAClosureAndHandlesTheResult() {
        def closure = Mock(Closure)
        def result = Mock(Task)

        when:
        context.add(closure)

        then:
        1 * closure.call() >> result
        1 * taskContext.add(result)
        0 * taskContext._
    }

    def invokesACallableAndHandlesTheResult() {
        def callable = Mock(Callable)
        def result = Mock(Task)

        when:
        context.add(callable)

        then:
        1 * callable.call() >> result
        1 * taskContext.add(result)
        0 * taskContext._
    }

    def flattensAnIterable() {
        def collection1 = Mock(FileCollection)
        def collection2 = Mock(FileCollection)
        def collection3 = Mock(FileCollection)

        when:
        context.add([collection1, [collection2, [collection3], []]])

        then:
        1 * taskContext.add(collection1)
        1 * taskContext.add(collection2)
        1 * taskContext.add(collection3)
        0 * taskContext._
    }

    def flattensAnArray() {
        def collection1 = Mock(FileCollection)
        def collection2 = Mock(FileCollection)
        def collection3 = Mock(FileCollection)

        when:
        context.add([collection1, [collection2, collection3] as Object[]] as Object[])

        then:
        1 * taskContext.add(collection1)
        1 * taskContext.add(collection2)
        1 * taskContext.add(collection3)
        0 * taskContext._
    }

    def ignoresOtherTypes() {
        when:
        context.add('a')
        context.add(Stub(TaskDependency))
        context.add(Stub(Runnable))

        then:
        0 * taskContext._
    }
}
