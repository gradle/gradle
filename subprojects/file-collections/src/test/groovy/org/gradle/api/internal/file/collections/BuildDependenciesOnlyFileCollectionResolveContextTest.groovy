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
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import spock.lang.Specification

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

    def delegatesToDependencyContainerToDetermineBuildDependencies() {
        def container = Mock(TaskDependencyContainer)

        when:
        context.add(container)

        then:
        1 * taskContext.add(container)
        0 * taskContext._
        0 * container._
    }

    interface TestFileSet extends MinimalFileSet, Buildable {
    }
}
