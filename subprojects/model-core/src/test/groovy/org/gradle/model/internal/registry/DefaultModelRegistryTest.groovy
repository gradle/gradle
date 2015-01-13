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
import org.gradle.internal.Factory
import org.gradle.model.collection.CollectionBuilder
import org.gradle.model.internal.core.*
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification
import spock.lang.Unroll

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
        thrown IllegalStateException // TODO - reports 'unknown element', should instead complain about unknown inputs
    }

    def "cannot register creator when element already known"() {
        given:
        registry.create(creator("foo", String, "value"))

        when:
        registry.create(creator("foo", Integer, 12))

        then:
        DuplicateModelException e = thrown()
        e.message == /Cannot create 'foo' using creation rule 'create foo as Integer' as the rule 'create foo as String' is already registered to create this model element./
    }

    def "cannot register creator when element already closed"() {
        given:
        registry.create(creator("foo", String, "value"))
        registry.get(ModelPath.path("foo"), ModelType.untyped())

        when:
        registry.create(creator("foo", Integer, 12))

        then:
        DuplicateModelException e = thrown()
        e.message == /Cannot create 'foo' using creation rule 'create foo as Integer' as the rule 'create foo as String' has already been used to create this model element./
    }

    def "rule cannot add link when element already known"() {
        def mutatorAction = Mock(Action)

        given:
        registry.create(creator("foo", Integer, 12))
        registry.apply(ModelActionRole.Mutate, nodeMutator("foo", Integer, mutatorAction))
        mutatorAction.execute(_) >> { MutableModelNode node ->
            node.addLink(creator("foo.bar", String, "12"))
            node.addLink(creator("foo.bar", Integer, 12))
        }

        when:
        registry.get(ModelPath.path("foo"), ModelType.untyped())

        then:
        ModelRuleExecutionException e = thrown()
        e.message == /Exception thrown while executing model rule: mutate foo as Integer/
        e.cause instanceof DuplicateModelException
        e.cause.message == /Cannot create 'foo.bar' using creation rule 'create foo.bar as Integer' as the rule 'create foo.bar as String' is already registered to create this model element./
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

    def "inputs for creator are bound when inputs later defined by some rule"() {
        def creatorAction = Mock(Transformer)
        def mutatorAction = Mock(Action)

        given:
        registry.create(creator("bar", String, "foo.child", creatorAction))
        registry.create(creator("foo", Integer, 12))
        registry.apply(ModelActionRole.Mutate, nodeMutator("foo", Integer, mutatorAction))
        mutatorAction.execute(_) >> { MutableModelNode node -> node.addLink(creator("foo.child", Integer, 12)) }
        creatorAction.transform(12) >> "[12]"

        expect:
        registry.get(ModelPath.path("foo"), ModelType.untyped()) // TODO - should not need this - the input can be inferred from the input path
        registry.get(ModelPath.path("bar"), ModelType.untyped()) == "[12]"
    }

    def "creator and mutators are invoked in order before element is closed"() {
        def action = Mock(Action)

        given:
        registry.create(creator("foo", Bean, new Bean(), action))
        registry.apply(ModelActionRole.Defaults, mutator("foo", Bean, action))
        registry.apply(ModelActionRole.Initialize, mutator("foo", Bean, action))
        registry.apply(ModelActionRole.Mutate, mutator("foo", Bean, action))
        registry.apply(ModelActionRole.Finalize, mutator("foo", Bean, action))
        registry.apply(ModelActionRole.Validate, mutator("foo", Bean, action))

        when:
        def value = registry.get(ModelPath.path("foo"), ModelType.of(Bean)).value

        then:
        value == "create > defaults > initialize > mutate > finalize"

        and:
        1 * action.execute(_) >> { Bean bean ->
            assert bean.value == null
            bean.value = "create"
        }
        1 * action.execute(_) >> { Bean bean ->
            bean.value += " > defaults"
        }
        1 * action.execute(_) >> { Bean bean ->
            bean.value += " > initialize"
        }
        1 * action.execute(_) >> { Bean bean ->
            bean.value += " > mutate"
        }
        1 * action.execute(_) >> { Bean bean ->
            bean.value += " > finalize"
        }
        1 * action.execute(_) >> { Bean bean ->
            assert bean.value == "create > defaults > initialize > mutate > finalize"
        }
        0 * action._

        when:
        registry.get(ModelPath.path("foo"), ModelType.of(Bean))

        then:
        0 * action._
    }

    def "creator for linked element invoked before element is closed"() {
        def action = Mock(Action)

        given:
        registry.create(creator("foo", Bean, new Bean()))
        registry.apply(ModelActionRole.Mutate, nodeMutator("foo", Bean, action))

        when:
        registry.get(ModelPath.path("foo"), ModelType.of(Bean))

        then:
        1 * action.execute(_) >> { MutableModelNode node -> node.addLink(creator("foo.bar", String, "value", action)) }
        1 * action.execute(_)
        0 * action._
    }

    def "inputs for mutator are bound when inputs already closed"() {
        def action = Mock(BiAction)

        given:
        registry.create(creator("foo", Integer, 12))
        registry.get(ModelPath.path("foo"), ModelType.untyped())
        registry.create(creator("bar", Bean, new Bean()))
        registry.apply(ModelActionRole.Mutate, mutator("bar", Bean, Integer, action))
        action.execute(_, 12) >> { bean, value -> bean.value = "[12]" }

        expect:
        registry.get(ModelPath.path("bar"), ModelType.of(Bean)).value == "[12]"
    }

    def "inputs for mutator are bound when inputs already known"() {
        def action = Mock(BiAction)

        given:
        registry.create(creator("foo", Integer, 12))
        registry.create(creator("bar", Bean, new Bean()))
        registry.apply(ModelActionRole.Mutate, mutator("bar", Bean, Integer, action))
        action.execute(_, 12) >> { bean, value -> bean.value = "[12]" }

        expect:
        registry.get(ModelPath.path("bar"), ModelType.of(Bean)).value == "[12]"
    }

    def "inputs for mutator are bound as inputs become known"() {
        def action = Mock(BiAction)

        given:
        registry.create(creator("bar", Bean, new Bean()))
        registry.apply(ModelActionRole.Mutate, mutator("bar", Bean, Integer, action))
        registry.create(creator("foo", Integer, 12))
        action.execute(_, 12) >> { bean, value -> bean.value = "[12]" }

        expect:
        registry.get(ModelPath.path("bar"), ModelType.of(Bean)).value == "[12]"
    }

    def "can attach a mutator with inputs to all elements linked from an element"() {
        def creatorAction = Mock(Action)
        def mutatorAction = Mock(BiAction)

        given:
        registry.create(nodeCreator("parent", Integer, creatorAction))
        creatorAction.execute(_) >> { MutableModelNode node ->
            node.applyToAllLinks(ModelActionRole.Mutate, mutator(null, Bean, String, mutatorAction))
            node.addLink(creator("parent.foo", Bean, new Bean(value: "foo")))
            node.addLink(creator("parent.bar", Bean, new Bean(value: "bar")))
        }
        mutatorAction.execute(_, _) >> { Bean bean, String prefix -> bean.value = "$prefix: $bean.value" }
        registry.create(creator("prefix", String, "prefix"))

        registry.get(ModelPath.path("parent"), ModelType.untyped()) // TODO - should not need this

        expect:
        registry.get(ModelPath.path("parent.foo"), ModelType.of(Bean)).value == "prefix: foo"
        registry.get(ModelPath.path("parent.bar"), ModelType.of(Bean)).value == "prefix: bar"
    }

    def "can attach a mutator to all elements with specific type linked from an element"() {
        def creatorAction = Mock(Action)
        def mutatorAction = Mock(Action)

        given:
        registry.create(nodeCreator("parent", Integer, creatorAction))
        creatorAction.execute(_) >> { MutableModelNode node ->
            node.applyToAllLinks(ModelActionRole.Mutate, mutator(null, Bean, mutatorAction))
            node.addLink(creator("parent.foo", String, "ignore me"))
            node.addLink(creator("parent.bar", Bean, new Bean(value: "bar")))
        }
        mutatorAction.execute(_) >> { Bean bean -> bean.value = "prefix: $bean.value" }

        registry.get(ModelPath.path("parent"), ModelType.untyped()) // TODO - should not need this

        expect:
        registry.get(ModelPath.path("parent.bar"), ModelType.of(Bean)).value == "prefix: bar"
        registry.get(ModelPath.path("parent.foo"), ModelType.of(String)) == "ignore me"
    }

    def "can attach a mutator with inputs to element linked from another element"() {
        def creatorAction = Mock(Action)
        def mutatorAction = Mock(BiAction)

        given:
        registry.create(nodeCreator("parent", Integer, creatorAction))
        creatorAction.execute(_) >> { MutableModelNode node ->
            node.applyToLink(ModelActionRole.Mutate, mutator("parent.foo", Bean, String, mutatorAction))
            node.addLink(creator("parent.foo", Bean, new Bean(value: "foo")))
            node.addLink(creator("parent.bar", Bean, new Bean(value: "bar")))
        }
        mutatorAction.execute(_, _) >> { Bean bean, String prefix -> bean.value = "$prefix: $bean.value" }
        registry.create(creator("prefix", String, "prefix"))

        registry.get(ModelPath.path("parent"), ModelType.untyped()) // TODO - should not need this

        expect:
        registry.get(ModelPath.path("parent.foo"), ModelType.of(Bean)).value == "prefix: foo"
        registry.get(ModelPath.path("parent.bar"), ModelType.of(Bean)).value == "bar"
    }

    def "cannot attach link when element is not mutable"() {
        def action = Stub(Action)

        given:
        registry.create(creator("thing", String, "value"))
        registry.apply(ModelActionRole.Validate, nodeMutator("thing", Object, action))
        action.execute(_) >> { MutableModelNode node -> node.addLink(creator("thing.child", String, "value")) }

        when:
        registry.get(ModelPath.path("thing"), ModelType.untyped())

        then:
        ModelRuleExecutionException e = thrown()
        e.cause instanceof IllegalStateException
        e.cause.message == "Cannot create 'thing.child' using creation rule 'create thing.child as String' as model element 'thing' is no longer mutable."
    }

    def "cannot set value when element is not mutable"() {
        def action = Stub(Action)

        given:
        registry.create(creator("thing", String, "value"))
        registry.apply(ModelActionRole.Validate, nodeMutator("thing", Object, action))
        action.execute(_) >> { MutableModelNode node -> node.setPrivateData(ModelType.of(String), "value 2") }

        when:
        registry.get(ModelPath.path("thing"), ModelType.untyped())

        then:
        ModelRuleExecutionException e = thrown()
        e.cause instanceof IllegalStateException
        e.cause.message == "Cannot set value for model element 'thing' as this element is not mutable."
    }

    @Unroll
    def "cannot add action for #targetState mutation when in #fromState mutation"() {
        def action = Stub(Action)

        given:
        registry.create(creator("thing", String, "value"))
        registry.apply(fromState, nodeMutator("thing", Object, action))
        action.execute(_) >> { MutableModelNode node -> registry.apply(targetState, mutator("thing", String, {})) }

        when:
        registry.get(ModelPath.path("thing"), ModelType.untyped())

        then:
        ModelRuleExecutionException e = thrown()
        e.cause instanceof IllegalStateException
        e.cause.message.startsWith "Cannot add $targetState rule 'thing' for model element 'thing'"

        where:
        fromState                  | targetState
        ModelActionRole.Initialize | ModelActionRole.Defaults
        ModelActionRole.Mutate     | ModelActionRole.Defaults
        ModelActionRole.Mutate     | ModelActionRole.Initialize
        ModelActionRole.Finalize   | ModelActionRole.Defaults
        ModelActionRole.Finalize   | ModelActionRole.Initialize
        ModelActionRole.Finalize   | ModelActionRole.Mutate
        ModelActionRole.Validate   | ModelActionRole.Defaults
        ModelActionRole.Validate   | ModelActionRole.Initialize
        ModelActionRole.Validate   | ModelActionRole.Mutate
        ModelActionRole.Validate   | ModelActionRole.Finalize
    }

    @Unroll
    def "can get node at state"() {
        given:
        registry.create(creator("thing", Bean, new Bean(value: "created")))
        ModelActionRole.values().each { role ->
            registry.apply(role, mutator("thing", Bean) {
                if (it) {
                    it.value = role.name()
                }
            })
        }

        expect:
        registry.get(ModelPath.path("thing"), state).getPrivateData(ModelType.of(Bean))?.value == expected

        where:
        state                           | expected
        ModelNode.State.Known           | null
        ModelNode.State.Created         | "created"
        ModelNode.State.DefaultsApplied | ModelActionRole.Defaults.name()
        ModelNode.State.Initialized     | ModelActionRole.Initialize.name()
        ModelNode.State.Mutated         | ModelActionRole.Mutate.name()
        ModelNode.State.Finalized       | ModelActionRole.Finalize.name()
        ModelNode.State.SelfClosed      | ModelActionRole.Validate.name()
        ModelNode.State.GraphClosed     | ModelActionRole.Validate.name()
    }

    def "asking for element at known state does not invoke creator"() {
        given:
        def events = []
        registry.create(creator("thing", Bean, new Bean(), { events << "created" } as Action))

        when:
        registry.get(ModelPath.path("thing"), ModelNode.State.Known)

        then:
        events == []

        when:
        registry.get(ModelPath.path("thing"), ModelNode.State.Created)

        then:
        events == ["created"]
    }

    @Unroll
    def "asking for unknown element at any state returns null"() {
        expect:
        registry.get(ModelPath.path("thing"), state) == null

        where:
        state << ModelNode.State.values().toList()
    }

    def "getting self closed collection defines all links but does not realise them until graph closed"() {
        given:
        def events = []
        def cbType = DefaultCollectionBuilder.typeOf(ModelType.of(Bean))
        registry.create(collection("things", Bean))
        registry.apply(ModelActionRole.Mutate, mutator("things", cbType) { c ->
            events << "collection mutated"
            c.create("c1") { events << "$it.name created" }
        })

        when:
        def cbNode = registry.get(ModelPath.path("things"), ModelNode.State.SelfClosed)

        then:
        events == ["collection mutated"]
        cbNode.links.keySet().toList() == ["c1"]

        when:
        registry.get(ModelPath.path("things"), ModelNode.State.GraphClosed)

        then:
        events == ["collection mutated", "c1 created"]
    }

    @Unroll
    def "cannot request model node at earlier state"() {
        given:
        registry.create(creator("thing", Bean, new Bean()))

        expect:
        registry.get(ModelPath.path("thing"), state)

        when:
        // This has to be in a when block to stop Spock rewriting it
        ModelNode.State.values().findAll { it.ordinal() < state.ordinal() }.each { earlier ->
            try {
                registry.get(ModelPath.path("thing"), earlier)
                throw new AssertionError("Expected error")
            } catch (IllegalStateException e) {
                // expected
            }
        }

        then:
        true

        where:
        state << ModelNode.State.values().toList()
    }

    @Unroll
    def "is benign to request element at current state"() {
        given:
        registry.create(creator("thing", Bean, new Bean()))

        when:
        // not in loop to get different stacktrace line numbers
        registry.get(ModelPath.path("thing"), state)
        registry.get(ModelPath.path("thing"), state)
        registry.get(ModelPath.path("thing"), state)

        then:
        noExceptionThrown()

        where:
        state << ModelNode.State.values().toList()
    }

    @Unroll
    def "requesting at current state does not reinvoke actions"() {
        given:
        def events = []
        registry.create(creator("thing", Bean, new Bean()))
        def uptoRole = ModelActionRole.values().findAll { it.ordinal() <= role.ordinal() }
        uptoRole.each { r ->
            registry.apply(r, mutator("thing", Bean) { events << r.name() })
        }

        when:
        registry.get(ModelPath.path("thing"), state)

        then:
        events == uptoRole*.name()

        when:
        registry.get(ModelPath.path("thing"), state)

        then:
        events == uptoRole*.name()

        where:
        state                           | role
        ModelNode.State.DefaultsApplied | ModelActionRole.Defaults
        ModelNode.State.Initialized     | ModelActionRole.Initialize
        ModelNode.State.Mutated         | ModelActionRole.Mutate
        ModelNode.State.Finalized       | ModelActionRole.Finalize
        ModelNode.State.SelfClosed      | ModelActionRole.Validate
        ModelNode.State.GraphClosed     | ModelActionRole.Validate
    }

    class Bean {
        String name
        String value
    }

    public <I> ModelCreator collection(String path, Class<I> itemType) {
        def itemModelType = ModelType.of(itemType)
        def collectionBuilderType = DefaultCollectionBuilder.typeOf(itemModelType)
        ModelCreators.of(ModelReference.of(path, collectionBuilderType)) { node, inputs ->
            node.setPrivateData(collectionBuilderType, new DefaultCollectionBuilder<I>(itemModelType, { name, type -> new Bean(name: name) }, [], new SimpleModelRuleDescriptor("collection creator"), node))
        }
        .withProjection(new UnmanagedModelProjection<CollectionBuilder<I>>(collectionBuilderType, true, true))
                .build()
    }


    public <C> ModelCreator creator(String path, Class<C> type, C value) {
        creator(path, type, { value } as Factory)
    }

    /**
     * Executes the given action when the model element is created.
     */
    public <C> ModelCreator creator(String path, Class<C> type, C value, Action<? super C> initializer) {
        creator(path, type, { initializer.execute(value); value } as Factory)
    }

    /**
     * Invokes the given factory to create the model element.
     */
    public <C> ModelCreator creator(String path, Class<C> type, Factory<? extends C> initializer) {
        def modelType = ModelType.of(type)
        ModelCreators.of(ModelReference.of(path, type), new BiAction<MutableModelNode, Inputs>() {
            @Override
            void execute(MutableModelNode mutableModelNode, Inputs inputs) {
                mutableModelNode.setPrivateData(modelType, initializer.create())
            }
        }).withProjection(new UnmanagedModelProjection(modelType, true, true))
                .simpleDescriptor("create $path as $type.simpleName")
                .build()
    }

    /**
     * Invokes the given transformer to take an input of given type and produce the value for the model element.
     */
    public <C, I> ModelCreator creator(String path, Class<C> type, Class<I> inputType, Transformer<? extends C, ? super I> action) {
        def modelType = ModelType.of(type)
        ModelCreators.of(ModelReference.of(path, type), new BiAction<MutableModelNode, Inputs>() {
            @Override
            void execute(MutableModelNode mutableModelNode, Inputs inputs) {
                mutableModelNode.setPrivateData(modelType, action.transform(inputs.get(0, ModelType.of(inputType)).instance))
            }
        }).withProjection(new UnmanagedModelProjection(modelType, true, true))
                .inputs([ModelReference.of(inputType)])
                .simpleDescriptor("create $path")
                .build()
    }

    ModelCreator nodeCreator(String path, Class<?> type, Action<? super MutableModelNode> action) {
        def modelType = ModelType.of(type)
        ModelCreators.of(ModelReference.of(path, type), new BiAction<MutableModelNode, Inputs>() {
            @Override
            void execute(MutableModelNode mutableModelNode, Inputs inputs) {
                action.execute(mutableModelNode)
            }
        }).withProjection(new UnmanagedModelProjection(modelType, true, true))
                .simpleDescriptor("create $path")
                .build()
    }

    /**
     * Invokes the given transformer to take an input of given path and produce the value for the model element.
     */
    public <C> ModelCreator creator(String path, Class<C> type, String inputPath, Transformer<? extends C, ?> action) {
        def modelType = ModelType.of(type)
        ModelCreators.of(ModelReference.of(path, type), new BiAction<MutableModelNode, Inputs>() {
            @Override
            void execute(MutableModelNode mutableModelNode, Inputs inputs) {
                mutableModelNode.setPrivateData(modelType, action.transform(inputs.get(0, ModelType.untyped()).instance))
            }
        }).withProjection(new UnmanagedModelProjection(modelType, true, true))
                .inputs([ModelReference.of(inputPath)])
                .simpleDescriptor("create $path")
                .build()
    }

    /**
     * Invokes the given action to mutate the value of the given element.
     */
    public <S> ModelAction<?> mutator(String path, ModelType<S> type, Action<? super S> action) {
        ModelAction mutator = Stub(ModelAction)
        mutator.subject >> (path == null ? ModelReference.of(type) : ModelReference.of(path, type))
        mutator.inputs >> []
        mutator.descriptor >> new SimpleModelRuleDescriptor(path)
        mutator.execute(_, _, _) >> { node, object, inputs ->
            action.execute(object)
        }
        return mutator
    }

    public <S> ModelAction<?> mutator(String path, Class<S> type, Action<? super S> action) {
        return mutator(path, ModelType.of(type), action)
    }

    /**
     * Invokes the given action to mutate the node for the given element.
     */
    public <S> ModelAction<S> nodeMutator(String path, Class<S> type, Action<? super MutableModelNode> action) {
        ModelAction mutator = Stub(ModelAction)
        mutator.subject >> (path == null ? ModelReference.of(type) : ModelReference.of(path, type))
        mutator.inputs >> []
        mutator.descriptor >> new SimpleModelRuleDescriptor("mutate $path as $type.simpleName")
        mutator.execute(_, _, _) >> { node, object, inputs ->
            action.execute(node)
        }
        return mutator
    }

    /**
     * Invokes the given action with the value for the given element and the given input, to mutate the value for the given element.
     */
    public <S, I> ModelAction<S> mutator(String path, Class<S> type, Class<I> inputType, BiAction<? super S, ? super I> action) {
        ModelAction mutator = Stub(ModelAction)
        mutator.subject >> (path == null ? ModelReference.of(type) : ModelReference.of(path, type))
        mutator.inputs >> [ModelReference.of(inputType)]
        mutator.descriptor >> new SimpleModelRuleDescriptor("mutate $path as $type.simpleName")
        mutator.execute(_, _, _) >> { node, object, inputs ->
            action.execute(object, inputs.get(0, ModelType.of(inputType)).instance)
        }
        return mutator
    }

}
