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

import org.gradle.api.Action
import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskContainer
import org.gradle.model.internal.core.ModelNode
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.registry.ModelRegistry
import spock.lang.Specification

class RealizableTaskCollectionTest extends Specification {

    def "realizes a nodes link of a given type"() {
        given:
        def project = Mock(ProjectInternal)
        ModelRegistryHelper registry = new ModelRegistryHelper()
        project.getModelRegistry() >> registry

        def events = []

        Action mutatorAction = mutator(registry, events, Mock(taskType), taskPath)

        registry.createInstance("tasks", Mock(TaskContainer))
        registry.mutate { it.path "tasks" node mutatorAction }

        when:
        new RealizableTaskCollection(realizableType, Mock(DefaultTaskCollection), project).realizeRuleTaskTypes()

        then:
        events == ["created task $taskPath"]

        where:
        realizableType | taskType  | taskPath
        BasicTask      | BasicTask | "tasks.basic"
        BasicTask      | ChildTask | "tasks.basicChild"
    }

    def "does not realise a node link for non-realisable types"() {
        given:
        def project = Mock(ProjectInternal)
        ModelRegistryHelper registry = new ModelRegistryHelper()
        project.getModelRegistry() >> registry

        def events = []

        Action basicAction = mutator(registry, events, Mock(BasicTask), "tasks.basic")
        Action redundantAction = mutator(registry, events, Mock(RedundantTask), "tasks.redundant")

        registry.createInstance("tasks", Mock(TaskContainer))
        registry.mutate { it.path "tasks" node basicAction }
        registry.mutate { it.path "tasks" node redundantAction }

        when:
        new RealizableTaskCollection(BasicTask, Mock(DefaultTaskCollection), project).realizeRuleTaskTypes()

        then:
        events == ['created task tasks.basic']
    }

    def "realizes tasks once only"() {
        given:
        def registry = Mock(ModelRegistry)
        def project = Mock(ProjectInternal)
        project.getModelRegistry() >> registry
        def node = Mock(MutableModelNode)
        node.getLinks(_) >> []


        when:
        RealizableTaskCollection collection = new RealizableTaskCollection(Class, Mock(TaskCollection), project)
        collection.iterator()
        collection.iterator()

        then:
        1 * registry.atStateOrLater(TaskContainerInternal.MODEL_PATH, ModelNode.State.SelfClosed) >> node
    }

    private Action mutator(ModelRegistryHelper registry, events, task, String path) {
        Action mutatorAction = Mock(Action)
        mutatorAction.execute(_) >> { MutableModelNode node ->
            node.addLink(registry.creator(path) {
                it.unmanaged(task, { events << "created task $path" })
            }
            )
        }
        return mutatorAction
    }
}

class BasicTask extends AbstractTask {}

class ChildTask extends BasicTask {}

class RedundantTask extends AbstractTask {}

