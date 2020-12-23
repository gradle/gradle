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

package org.gradle.api.internal.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskCollection
import org.gradle.model.internal.core.ModelNode
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultRealizableTaskCollectionTest extends Specification {

    def instantiator = TestUtil.instantiatorFactory().decorateLenient()

    def "realizes a nodes link of a given type when task dependencies visited"() {
        given:
        ModelRegistryHelper registry = new ModelRegistryHelper()
        ModelPath path = ModelPath.path("tasks")
        registry.registerInstance("tasks", "foo tasks")
            .mutate {
            it.path "tasks" node {
                it.addLinkInstance(taskPath, Mock(realizableType))
            }
        }

        when:
        new DefaultRealizableTaskCollection(realizableType, Stub(TaskCollection), registry.node(path), instantiator).visitDependencies(Stub(TaskDependencyResolveContext))

        then:
        registry.state(taskPath) == ModelNode.State.GraphClosed

        where:
        realizableType | taskType  | taskPath
        BasicTask      | BasicTask | "tasks.basic"
        BasicTask      | ChildTask | "tasks.basicChild"
    }

    def "does not realise a node link for non-realisable types"() {
        given:
        ModelRegistryHelper registry = new ModelRegistryHelper()
        ModelPath path = ModelPath.path("tasks")

        registry.registerInstance("tasks", "foo tasks")
            .mutate {
            it.path "tasks" node {
                it.addLinkInstance("tasks.redundant", Mock(RedundantTask))
            }
        }

        when:
        def collection = new DefaultRealizableTaskCollection(BasicTask, Stub(TaskCollection), registry.node(path), instantiator)
        collection.visitDependencies(Stub(TaskDependencyResolveContext))

        then:
        registry.state("tasks.redundant") == ModelNode.State.Discovered
    }

    def "realize is idempotent"() {
        given:
        ModelRegistryHelper registry = new ModelRegistryHelper()
        ModelPath path = ModelPath.path("tasks")

        registry.registerInstance("tasks", "foo tasks")
            .mutate {
            it.path "tasks" node {
                it.addLinkInstance("tasks.redundant", Mock(RedundantTask))
            }
        }


        when:
        DefaultRealizableTaskCollection collection = new DefaultRealizableTaskCollection(Class, Stub(TaskCollection), registry.node(path), instantiator)
        collection.visitDependencies(Stub(TaskDependencyResolveContext))
        collection.visitDependencies(Stub(TaskDependencyResolveContext))

        then:
        noExceptionThrown()
    }
}

class BasicTask extends DefaultTask {}

class ChildTask extends BasicTask {}

class RedundantTask extends DefaultTask {}

