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

import org.gradle.api.internal.AbstractTask
import org.gradle.api.tasks.TaskCollection
import org.gradle.model.internal.core.ModelNode
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.fixture.ModelRegistryHelper
import spock.lang.Specification

class RealizableTaskCollectionTest extends Specification {

    def "realizes a nodes link of a given type"() {
        given:
        ModelRegistryHelper registry = new ModelRegistryHelper()
        ModelPath path = ModelPath.path("tasks")
        registry.createInstance("tasks", "foo tasks")
            .mutate {
            it.path "tasks" node {
                it.addLink(registry.instanceCreator(taskPath, Mock(realizableType)))
            }
        }

        when:
        new RealizableTaskCollection(realizableType, Mock(DefaultTaskCollection), registry.node(path)).realizeRuleTaskTypes()

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

        registry.createInstance("tasks", "foo tasks")
            .mutate {
            it.path "tasks" node {
                it.addLink(registry.instanceCreator("tasks.redundant", Mock(RedundantTask)))
            }
        }

        when:
        def collection = new RealizableTaskCollection(BasicTask, Mock(DefaultTaskCollection), registry.node(path))
        collection.realizeRuleTaskTypes()

        then:
        registry.state("tasks.redundant") == ModelNode.State.Known
    }

    def "realize is idempotent"() {
        given:
        ModelRegistryHelper registry = new ModelRegistryHelper()
        ModelPath path = ModelPath.path("tasks")

        registry.createInstance("tasks", "foo tasks")
            .mutate {
            it.path "tasks" node {
                it.addLink(registry.instanceCreator("tasks.redundant", Mock(RedundantTask)))
            }
        }


        when:
        RealizableTaskCollection collection = new RealizableTaskCollection(Class, Mock(TaskCollection), registry.node(path))
        collection.realizeRuleTaskTypes()
        collection.realizeRuleTaskTypes()

        then:
        noExceptionThrown()
    }
}

class BasicTask extends AbstractTask {}

class ChildTask extends BasicTask {}

class RedundantTask extends AbstractTask {}

