/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal.registry

import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.internal.BiAction
import org.gradle.model.internal.core.*
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification
import org.gradle.internal.Factory

class DefaultModelRegistryTest extends Specification {

    def registry = new DefaultModelRegistry()

    def "can maybe get non existing"() {
        when:
        registry.get(ModelPath.path("foo"), ModelType.untyped())

        then:
        thrown IllegalStateException

        when:
        registry.find(ModelPath.path("foo"), ModelType.untyped()) == null

        then:
        noExceptionThrown()
    }

    def "can get element for which a creator has been registered"() {
        given:
        registry.create(creator("foo", String, "value"))

        expect:
        registry.get(ModelPath.path("foo"), ModelType.untyped()) == "value"
    }

    def "cannot get element for which creator inputs are not bound"() {
        given:
        registry.create(creator("foo", String, "other", Stub(Transformer)))

        when:
        registry.get(ModelPath.path("foo"), ModelType.untyped())

        then:
        thrown IllegalStateException // TODO - current reports 'unknown element', should instead complain about unknown inputs
    }

    def "inputs for creator are bound when inputs already closed"() {
        def action = Mock(Transformer)

        given:
        registry.create(creator("foo", Integer, 12))
        registry.get(ModelPath.path("foo"), ModelType.untyped())
        registry.create(creator("bar", String, Integer, action))
        action.transform(12) >> "[12]"

        expect:
        registry.get(ModelPath.path("bar"), ModelType.untyped()) == "[12]"
    }

    def "inputs for creator are bound when inputs already known"() {
        def action = Mock(Transformer)

        given:
        registry.create(creator("foo", Integer, 12))
        registry.create(creator("bar", String, Integer, action))
        action.transform(12) >> "[12]"

        expect:
        registry.get(ModelPath.path("bar"), ModelType.untyped()) == "[12]"
    }

    def "inputs for creator are bound as inputs become known"() {
        def action = Mock(Transformer)

        given:
        registry.create(creator("bar", String, Integer, action))
        registry.create(creator("foo", Integer, 12))
        action.transform(12) >> "[12]"

        expect:
        registry.get(ModelPath.path("bar"), ModelType.untyped()) == "[12]"
    }

    def "inputs for creator are bound as inputs defined by some rule"() {
        def creatorAction = Mock(Transformer)
        def mutatorAction = Mock(Action)

        given:
        registry.create(creator("bar", String, "foo.child", creatorAction))
        registry.create(creator("foo", Integer, 12))
        registry.mutate(nodeMutator("foo", Integer, mutatorAction))
        mutatorAction.execute(_) >> { MutableModelNode node -> node.addLink(creator("foo.child", Integer, 12)) }
        creatorAction.transform(12) >> "[12]"

        expect:
        registry.get(ModelPath.path("foo"), ModelType.untyped()) // TODO - should not need this - the input can be inferred from the input path
        registry.get(ModelPath.path("bar"), ModelType.untyped()) == "[12]"
    }

    def "creator mutator and finalizer are invoked before element is closed"() {
        def action = Mock(Action)

        given:
        registry.create(creator("foo", Bean, new Bean(), action))
        registry.mutate(mutator("foo", Bean, action))
        registry.finalize(mutator("foo", Bean, action))

        when:
        registry.get(ModelPath.path("foo"), ModelType.of(Bean)).value == "finalizer"

        then:
        1 * action.execute(_) >> { Bean bean -> bean.value = "creator" }
        1 * action.execute(_) >> { Bean bean -> bean.value = "mutator" }
        1 * action.execute(_) >> { Bean bean -> bean.value = "finalizer" }
        0 * action._
    }

    def "inputs for mutator are bound when inputs already closed"() {
        def action = Mock(BiAction)

        given:
        registry.create(creator("foo", Integer, 12))
        registry.get(ModelPath.path("foo"), ModelType.untyped())
        registry.create(creator("bar", Bean, new Bean()))
        registry.mutate(mutator("bar", Bean, Integer, action))
        action.execute(_, 12) >> { bean, value -> bean.value = "[12]" }

        expect:
        registry.get(ModelPath.path("bar"), ModelType.of(Bean)).value == "[12]"
    }

    def "inputs for mutator are bound when inputs already known"() {
        def action = Mock(BiAction)

        given:
        registry.create(creator("foo", Integer, 12))
        registry.create(creator("bar", Bean, new Bean()))
        registry.mutate(mutator("bar", Bean, Integer, action))
        action.execute(_, 12) >> { bean, value -> bean.value = "[12]" }

        expect:
        registry.get(ModelPath.path("bar"), ModelType.of(Bean)).value == "[12]"
    }

    def "inputs for mutator are bound as inputs become known"() {
        def action = Mock(BiAction)

        given:
        registry.create(creator("bar", Bean, new Bean()))
        registry.mutate(mutator("bar", Bean, Integer, action))
        registry.create(creator("foo", Integer, 12))
        action.execute(_, 12) >> { bean, value -> bean.value = "[12]" }

        expect:
        registry.get(ModelPath.path("bar"), ModelType.of(Bean)).value == "[12]"
    }

    class Bean {
        String value
    }

    ModelCreator creator(String path, Class<?> type, def value) {
        creator(path, type, { value } as Factory)
    }

    ModelCreator creator(String path, Class<?> type, def value, Action<?> initializer) {
        creator(path, type, { initializer.execute(value); value } as Factory)
    }

    ModelCreator creator(String path, Class<?> type, Factory<?> initializer) {
        def modelType = ModelType.of(type)
        ModelCreators.of(ModelReference.of(path, type), new BiAction<MutableModelNode, Inputs>() {
            @Override
            void execute(MutableModelNode mutableModelNode, Inputs inputs) {
                mutableModelNode.setPrivateData(modelType, initializer.create())
            }
        }).withProjection(new UnmanagedModelProjection(modelType, true, true))
                .simpleDescriptor(path)
                .build()
    }

    ModelCreator creator(String path, Class<?> type, Class<?> inputType, Transformer<?, ?> action) {
        def modelType = ModelType.of(type)
        ModelCreators.of(ModelReference.of(path, type), new BiAction<MutableModelNode, Inputs>() {
            @Override
            void execute(MutableModelNode mutableModelNode, Inputs inputs) {
                mutableModelNode.setPrivateData(modelType, action.transform(inputs.get(0, ModelType.of(inputType)).instance))
            }
        }).withProjection(new UnmanagedModelProjection(modelType, true, true))
                .inputs([ModelReference.of(inputType)])
                .simpleDescriptor(path)
                .build()
    }

    ModelCreator creator(String path, Class<?> type, String inputPath, Transformer<?, ?> action) {
        def modelType = ModelType.of(type)
        ModelCreators.of(ModelReference.of(path, type), new BiAction<MutableModelNode, Inputs>() {
            @Override
            void execute(MutableModelNode mutableModelNode, Inputs inputs) {
                mutableModelNode.setPrivateData(modelType, action.transform(inputs.get(0, ModelType.untyped()).instance))
            }
        }).withProjection(new UnmanagedModelProjection(modelType, true, true))
                .inputs([ModelReference.of(inputPath)])
                .simpleDescriptor(path)
                .build()
    }

    ModelMutator<?> mutator(String path, Class<?> type, Action<?> action) {
        ModelMutator mutator = Stub(ModelMutator)
        mutator.subject >> ModelReference.of(path, type)
        mutator.inputs >> []
        mutator.descriptor >> new SimpleModelRuleDescriptor(path)
        mutator.mutate(_, _, _) >> { node, object, inputs ->
            action.execute(object)
        }
        return mutator
    }

    ModelMutator<?> nodeMutator(String path, Class<?> type, Action<?> action) {
        ModelMutator mutator = Stub(ModelMutator)
        mutator.subject >> ModelReference.of(path, type)
        mutator.inputs >> []
        mutator.descriptor >> new SimpleModelRuleDescriptor(path)
        mutator.mutate(_, _, _) >> { node, object, inputs ->
            action.execute(node)
        }
        return mutator
    }

    ModelMutator<?> mutator(String path, Class<?> type, Class<?> inputType, BiAction<?, ?> action) {
        ModelMutator mutator = Stub(ModelMutator)
        mutator.subject >> ModelReference.of(path, type)
        mutator.inputs >> [ModelReference.of(inputType)]
        mutator.descriptor >> new SimpleModelRuleDescriptor(path)
        mutator.mutate(_, _, _) >> { node, object, inputs ->
            action.execute(object, inputs.get(0, ModelType.of(inputType)).instance)
        }
        return mutator
    }
}
