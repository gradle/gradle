/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.api.Task
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.ProviderResolutionStrategy
import org.gradle.api.internal.provider.ValueSupplier
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.Factory
import org.gradle.internal.file.PathToFileResolver
import spock.lang.Specification

class ProviderBackedFileCollectionTest extends Specification {
    def provider = Mock(ProviderInternal)
    def resolver = Mock(PathToFileResolver)
    def taskDependencyFactory = TestFiles.taskDependencyFactory()
    def patternSetFactory = Mock(Factory)
    def fileCollection = new ProviderBackedFileCollection(provider, resolver, taskDependencyFactory, patternSetFactory, ProviderResolutionStrategy.REQUIRE_PRESENT)

    def "resolves task dependencies for provider with known producer"() {
        def task = Stub(Task)

        when:
        def dependencies = fileCollection.buildDependencies

        then:
        0 * _

        when:
        def result = dependencies.getDependencies(null)

        then:
        1 * provider.producer >> ValueSupplier.ValueProducer.task(task)
        result == [task] as Set
    }

    def "resolves task dependencies for provide with unknown producer and file value"() {
        when:
        def dependencies = fileCollection.buildDependencies

        then:
        0 * _

        when:
        def result = dependencies.getDependencies(null)

        then:
        1 * provider.producer >> ValueSupplier.ValueProducer.unknown()
        1 * provider.get() >> 'ignore'
        result.empty
    }

    def "resolves task dependencies for provide with unknown producer and buildable value"() {
        def task = Stub(Task)
        def value = Mock(Buildable)

        when:
        def dependencies = fileCollection.buildDependencies

        then:
        0 * _

        when:
        def result = dependencies.getDependencies(null)

        then:
        1 * provider.producer >> ValueSupplier.ValueProducer.unknown()
        1 * provider.get() >> value
        1 * value.buildDependencies >> Stub(TaskDependency) {
            _ * getDependencies(_) >> ([task] as Set)
        }
        0 * resolver._
        result == [task] as Set
    }
}
