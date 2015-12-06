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

package org.gradle.model

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.internal.ClosureBackedAction
import org.gradle.model.internal.core.DeferredModelAction
import org.gradle.model.internal.core.ModelActionRole
import org.gradle.model.internal.core.ModelNode
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.fixture.ProjectRegistrySpec
import org.gradle.model.internal.manage.instance.ManagedInstance
import org.gradle.model.internal.registry.UnboundModelRulesException
import org.gradle.model.internal.type.ModelType
import org.gradle.model.internal.type.ModelTypes

import static org.gradle.util.TextUtil.normaliseLineSeparators

abstract class NodeBackedModelMapSpec<T extends Named, S extends T & Special> extends ProjectRegistrySpec {
    abstract Class<T> getItemClass()
    abstract Class<S> getSpecialItemClass()
    ModelType<T> getItemType() { ModelType.of(itemClass) }
    ModelType<S> getSpecialItemType() { ModelType.of(specialItemClass) }
    ModelPath path = ModelPath.path("map")

    ModelType<ModelMap<T>> getModelMapType() {
        ModelTypes.modelMap(itemType)
    }

    void mutate(@DelegatesTo(ModelMap) Closure<?> action) {
        registry.mutate(ModelReference.of(path, modelMapType), ClosureBackedAction.of(action))
    }

    void mutateWithoutDelegation(Action<ModelMap<T>> action) {
        registry.mutate(ModelReference.of(path, modelMapType), action)
    }

    void realize() {
        registry.realizeNode(path)
    }

    void selfClose() {
        registry.atState(path, ModelNode.State.SelfClosed)
    }

    ModelMap<T> realizeAsModelMap() {
        registry.realize(path, modelMapType)
    }

    T realizeChild(String name) {
        registry.realize(path.child(name), itemType)
    }

    def "can define an item with name"() {
        when:
        mutate { create("foo") }
        realize()

        then:
        realizeChild("foo").name == "foo"
    }

    def "does not eagerly create item"() {
        when:
        mutate {
            create("foo")
            create("bar")
        }
        selfClose()

        then:
        registry.state(path.child("foo")) == ModelNode.State.Registered

        when:
        realize()

        then:
        registry.state(path.child("foo")) == ModelNode.State.GraphClosed
    }

    def "can define item with custom type"() {
        when:
        mutateWithoutDelegation { it.create("foo", specialItemClass) }
        realize()

        then:
        specialItemClass.isInstance(realizeChild("foo"))
    }

    def "can define item using filtered collection"() {
        when:
        mutateWithoutDelegation {
            it.withType(specialItemClass).create("foo")
            it.withType(itemClass).create("bar")
        }
        realize()

        then:
        specialItemClass.isInstance(realizeChild("foo"))
        itemClass.isInstance(realizeChild("bar"))
        !specialItemClass.isInstance(realizeChild("bar"))
    }

    def "fails when using filtered collection to define item of type that is not assignable to collection item type"() {
        when:
        mutate {
            withType(String).create("foo")
        }
        realize()

        then:
        ModelRuleExecutionException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Cannot create an item of type java.lang.String as this is not a subtype of $itemClass.name."
    }

    def "can register config rules for item"() {
        when:
        mutate {
            create("foo") {
                other = "changed"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "changed"
    }

    def "can register config rule and type for item"() {
        when:
        mutateWithoutDelegation {
            it.create("foo", specialItemClass) {
                other = "changed"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "changed"
    }

    def "can access values"() {
        when:
        mutate {
            create("a") { other = "first" }
            create("b") { other = "second" }
        }

        then:
        realizeAsModelMap().values()*.other as Set == ["first", "second"] as Set
    }

    def "can register mutate rule for item with name"() {
        when:
        mutate {
            named("foo") {
                assert other == "original"
                other = "changed"
            }
            create("foo") {
                other = "original"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "changed"
    }

    def "can register mutate rule for item with name using filtered container"() {
        when:
        mutateWithoutDelegation {
            it.withType(Object).named("foo") {
                other += " Object"
            }
            it.withType(Special).named("foo") {
                other += " Special"
            }
            it.withType(specialItemClass).named("foo") {
                other += " SpecialItem"
            }
            it.create("foo", specialItemClass) {
                other = "types:"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "types: Object Special SpecialItem"
    }

    def "fails when named item does not have view with appropriate type"() {
        when:
        mutate {
            withType(String).named("foo") {
            }
            create("foo")
        }
        realize()

        then:
        InvalidModelRuleException e = thrown()
        e.cause instanceof ModelRuleBindingException
        e.cause.message.startsWith("Model reference to element '${path.child('foo')}' with type java.lang.String is invalid due to incompatible types.")
    }

    /**
     * This test documents the current behaviour, not necessarily the desired.
     *
     * Ideally, we'd get a failure here indicating that container item 'foo' is not String & NamedThing
     */
    def "rules targeting item of mismatched type are allowed"() {
        def classLoader = new GroovyClassLoader(getClass().classLoader)
        def SetOtherToName = classLoader.parseClass """
            import org.gradle.model.*

            class SetOtherToName extends RuleSource {
                @Mutate
                void set($itemClass.name thing) {
                    thing.other = thing.name
                }
            }
        """

        when:
        mutate {
            withType(String).named("foo", SetOtherToName)
            create("foo")
        }
        realize()

        then:
        realizeChild("foo").other == "foo"
    }

    def "can register mutate rule for all items using filtered container"() {
        when:
        mutateWithoutDelegation {
            it.withType(Named).all {
                other += " Named"
            }
            it.withType(String).all {
                other += " String"
            }
            it.withType(itemClass).all {
                other += " Item"
            }
            it.withType(Special).all {
                other += " Special"
            }
            it.withType(specialItemClass).all {
                other += " SpecialItem"
            }
            it.create("foo") {
                other = "types:"
            }
            it.create("bar", specialItemClass) {
                other = "types:"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "types: Named Item"
        realizeChild("bar").other == "types: Named Item Special SpecialItem"
    }

    def "can register mutate rule for all items"() {
        when:
        mutate {
            all {
                assert other == "original"
                other = "changed"
            }
            create("foo") {
                other = "original"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "changed"
    }

    def "can register mutate rule for all items with specific type"() {
        when:
        mutateWithoutDelegation {
            it.withType(Named) {
                other += " Named"
            }
            it.withType(String) {
                other += " String"
            }
            it.withType(Special) {
                other += " Special"
            }
            it.withType(specialItemClass) {
                other += " SpecialItem"
            }
            it.create("foo") {
                other = "foo:"
            }
            it.create("bar", specialItemClass) {
                other = "bar:"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "foo: Named"
        realizeChild("bar").other == "bar: Named Special SpecialItem"
    }

    def "can register defaults rule for all items"() {
        when:
        mutate {
            all {
                other += " all{}"
            }
            create("foo") {
                other += " create()"
            }
            beforeEach {
                other = "beforeEach{}"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "beforeEach{} create() all{}"
    }

    def "can register defaults rule for all items with type"() {
        when:
        mutateWithoutDelegation {
            it.beforeEach(Named) {
                other = "Named"
            }
            it.beforeEach(String) {
                other += " String"
            }
            it.beforeEach(Special) {
                other += " Special"
            }
            it.beforeEach(specialItemClass) {
                other += " SpecialItem"
            }
            it.create("foo") {
                other += " create(foo)"
            }
            it.create("bar", specialItemClass) {
                other += " create(bar)"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "Named create(foo)"
        realizeChild("bar").other == "Named Special SpecialItem create(bar)"
    }

    def "can register finalize rule for all items"() {
        when:
        mutate {
            all {
                other += " all{}"
            }
            afterEach {
                other += " afterEach{}"
            }
            create("foo") {
                other = "create()"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "create() all{} afterEach{}"
    }

    def "cannot add when realized"() {
        when:
        realizeAsModelMap().create("foo")

        then:
        thrown ModelViewClosedException
    }

    def "is managed instance"() {
        when:
        mutate {
            assert it instanceof ManagedInstance
            assert withType(SpecialNamedThingInterface) instanceof ManagedInstance
        }

        then:
        realize()
    }

    def "provides groovy DSL to create and configure items"() {
        when:
        mutateWithoutDelegation {
            it.foo {
                assert other == "original"
                other = "changed"
            }
            it.foo(itemClass) {
                other = "original"
            }
            it.bar(specialItemClass)
        }
        realize()

        then:
        realizeChild("foo").other == "changed"
        specialItemClass.isInstance(realizeChild("bar"))
    }

    def "can create item using transformed DSL rule closure"() {
        // DSL rules are represented using DeferredModelAction
        def action = mockDeferredModelAction()

        given:
        action.getDescriptor() >> new SimpleModelRuleDescriptor("action")
        action.execute(_, _) >> { MutableModelNode node, ModelActionRole role ->
            def thing = node.asMutable(itemType, Stub(ModelRuleDescriptor)).instance
            thing.other = "changed"
        }

        when:
        mutateWithoutDelegation {
            it.a(itemClass, action)
            it.b(specialItemClass, action)
            it.create('c', action)
            it.create('d', specialItemClass, action)
        }

        then:
        realizeChild("a").other == "changed"
        realizeChild("b").other == "changed"
        specialItemClass.isInstance realizeChild("b")
        realizeChild("c").other == "changed"
        realizeChild("d").other == "changed"
        specialItemClass.isInstance realizeChild("d")
    }

    def "can define config rules for named item using transformed DSL rule closure"() {
        // DSL rules are represented using DeferredModelAction
        def action = mockDeferredModelAction()

        given:
        action.execute(_, _) >> { MutableModelNode node, ModelActionRole role ->
            def thing = node.asMutable(itemType, Stub(ModelRuleDescriptor)).instance
            thing.other = "changed"
        }

        when:
        mutateWithoutDelegation {
            it.a(action)
            it.named('b', action)
            it.a(itemClass)
            it.b(itemClass)
        }

        then:
        realizeChild("a").other == "changed"
        realizeChild("b").other == "changed"
    }

    def "can define config rules for all item using transformed DSL rule closure"() {
        // DSL rules are represented using DeferredModelAction
        def action = mockDeferredModelAction()

        given:
        action.execute(_, _) >> { MutableModelNode node, ModelActionRole role ->
            def thing = node.asMutable(itemType, Stub(ModelRuleDescriptor)).instance
            thing.other = "changed"
        }

        when:
        mutate {
            all(action)
            create('a')
            create('b')
        }

        then:
        realizeChild("a").other == "changed"
        realizeChild("b").other == "changed"
    }

    def "can define config rules for all item with given type using transformed DSL rule closure"() {
        // DSL rules are represented using DeferredModelAction
        def action = mockDeferredModelAction()

        given:
        action.execute(_, _) >> { MutableModelNode node, ModelActionRole role ->
            def thing = node.asMutable(itemType, Stub(ModelRuleDescriptor)).instance
            thing.other = "changed"
        }

        when:
        mutateWithoutDelegation {
            it.withType(specialItemClass, action)
            it.create('a') { other = "original" }
            it.create('b', specialItemClass)
        }

        then:
        realizeChild("a").other == "original"
        realizeChild("b").other == "changed"
    }

    def "can define default rules for all item using transformed DSL rule closure"() {
        // DSL rules are represented using DeferredModelAction
        def action = mockDeferredModelAction()

        given:
        action.execute(_, _) >> { MutableModelNode node, ModelActionRole role ->
            def thing = node.asMutable(itemType, Stub(ModelRuleDescriptor)).instance
            thing.other = "default"
        }

        when:
        mutate {
            create('a') { other = "[$other]" }
            create('b')
            beforeEach(action)
        }

        then:
        realizeChild("a").other == "[default]"
        realizeChild("b").other == "default"
    }

    def "can define finalize rules for all item using transformed DSL rule closure"() {
        // DSL rules are represented using DeferredModelAction
        def action = mockDeferredModelAction()

        given:
        action.execute(_, _) >> { MutableModelNode node, ModelActionRole role ->
            def thing = node.asMutable(itemType, Stub(ModelRuleDescriptor)).instance
            thing.other = "[$thing.other]"
        }

        when:
        mutate {
            afterEach(action)
            create('a') { other = "a" }
            create('b') { other = "b" }
        }

        then:
        realizeChild("a").other == "[a]"
        realizeChild("b").other == "[b]"
    }

    static class MutableValue {
        String value
    }

    static class Bean {
        String name
        String value
    }

    static class SpecialBean extends Bean {
        String other
    }

    def "sensible error is thrown when trying to apply a class that does not extend RuleSource as a scoped rule"() {
        def mmType = ModelTypes.modelMap(MutableValue)

        registry
            .registerModelMap("values", MutableValue) { it.registerFactory(MutableValue) { new MutableValue() } }
            .mutate {
                it.descriptor("mutating elements").path "values" type mmType action { c ->
                    c.create("element")
                    c.named("element", Object)
                }
            }

        when:
        registry.realize("values", ModelType.UNTYPED)

        then:
        ModelRuleExecutionException e = thrown()
        e.cause.class == InvalidModelRuleDeclarationException
        e.cause.message == "Type java.lang.Object is not a valid rule source: rule source classes must directly extend org.gradle.model.RuleSource"
    }
    static class ElementRules extends RuleSource {
        @Mutate
        void connectElementToInput(Bean element, String input) {
            element.value = input
        }
    }

    def "inputs of a rule from an inner source are not realised if the rule is not required"() {
        given:
        def mmType = ModelTypes.modelMap(Bean)
        def events = []
        registry
            .registerInstance("input", "input") { events << "input created" }
            .registerModelMap("beans", Bean) { it.registerFactory(Bean) { new Bean(name: it) } }
            .mutate {
                it.path "beans" type mmType action { c ->
                    events << "collection mutated"
                    c.create("element") { events << "$it.name created" }
                    c.named("element", ElementRules)
                }
            }

        when:
        registry.atState(ModelPath.path("beans"), ModelNode.State.SelfClosed)

        then:
        events == ["collection mutated"]

        when:
        registry.atState(ModelPath.path("beans"), ModelNode.State.GraphClosed)

        then:
        events == ["collection mutated", "element created", "input created"]
    }

    def "model rule with by-path dependency on non task related collection element's child that does exist passes validation"() {
        def mmType = ModelTypes.modelMap(Bean)

        registry
            .registerInstance("foo", new Bean())
            .mutate {
                it.path("foo").type(Bean).action("beans.element.mutable", ModelType.of(MutableValue)) { Bean subject, MutableValue input ->
                    subject.value = input.value
                }
            }
            .registerModelMap("beans", Bean) { it.registerFactory(Bean) { new Bean(name: it) } }
                .mutate {
                it.path "beans" type mmType action { c ->
                    c.create("element")
                }
            }
            .mutate {
                it.path "beans.element" node {
                    it.addLinkInstance("beans.element.mutable", new MutableValue(value: "bar"))
                }
            }

        when:
        registry.bindAllReferences()

        then:
        noExceptionThrown()
    }

    static class ByTypeSubjectBoundToScopeChildRule extends RuleSource {
        @Mutate
        void mutateScopeChild(MutableValue value) {
            value.value = "foo"
        }
    }

    def "model rule with by-type dependency on non task related collection element's child that does exist passes validation"() {
        given:
        def mmType = ModelTypes.modelMap(Bean)

        registry
            .registerModelMap("beans", Bean) { it.registerFactory(Bean) { new Bean(name: it) } }
            .mutate {
                it.path "beans" type mmType action { c ->
                    c.create("element")
                    c.named("element", ByTypeSubjectBoundToScopeChildRule)
                }
            }
            .mutate {
                it.path "beans.element" descriptor "element child" node {
                    it.addLinkInstance("beans.element.mutable", new MutableValue())
                }
            }

        when:
        registry.bindAllReferences()

        then:
        noExceptionThrown()
    }

    def "adding an unbound scoped rule for an element that is never created results in an error upon validation if the scope parent has been self closed"() {
        given:
        def mmType = ModelTypes.modelMap(Bean)

        registry
            .registerModelMap("beans", Bean) { it.registerFactory(Bean) { new Bean(name: it) } }
            .mutate {
                it.path "beans" type mmType action { c ->
                    c.named("element", ElementRules)
                }
            }

        when:
        registry.atState(ModelPath.path("beans"), ModelNode.State.SelfClosed)
        registry.bindAllReferences()

        then:
        UnboundModelRulesException e = thrown()
        normaliseLineSeparators(e.message).contains('''
  NodeBackedModelMapSpec.ElementRules#connectElementToInput
    subject:
      - <no path> NodeBackedModelMapSpec.Bean (parameter 1) [*]
          scope: beans.element
    inputs:
      - <no path> String (parameter 2) [*]
''')
    }

    static class SetOther extends RuleSource {
        @Mutate
        void set(SpecialBean bean, String other) {
            bean.other = other
            bean.value = "changed"
        }
    }

    def "can add rule source to all items of type"() {
        given:
        def mmType = ModelTypes.modelMap(Bean)
        registry
            .registerModelMap("beans", Bean) {
                it.registerFactory(Bean) { new Bean(name: it) }
                it.registerFactory(SpecialBean) { new SpecialBean(name: it) }
            }
            .registerInstance("s", "other")
            .mutate {
                it.path("beans").type(mmType).action { c ->
                    c.create("b1", Bean)
                    c.create("b2", Bean)
                    c.create("sb1", SpecialBean)
                    c.create("sb2", SpecialBean)
                    c.withType(SpecialBean, SetOther)
                }
            }

        expect:
        registry.node("s").state == ModelNode.State.Registered

        when:
        registry.atState("beans", ModelNode.State.SelfClosed)

        then:
        registry.node("s").state == ModelNode.State.Registered
        registry.get("beans.b1", Bean).value != "changed"
        registry.node("s").state == ModelNode.State.Registered

        when:
        def sb2 = registry.get("beans.sb2", SpecialBean)

        then:
        sb2.other == "other"
        registry.node("s").state == ModelNode.State.GraphClosed

        when:
        def sb1 = registry.get("beans.sb1", SpecialBean)

        then:
        sb1.other == "other"
    }

    static class SetProp extends RuleSource {
        @Mutate
        void m(@Path("foo") Bean bean) {}
    }

    def "when targeting by type, paths are interpreted relative to item"() {
        given:
        def mmType = ModelTypes.modelMap(Bean)

        registry
            .registerModelMap("beans", Bean) {
                it.registerFactory(Bean) { new Bean(name: it) }
                it.registerFactory(SpecialBean) { new SpecialBean(name: it) }
            }
            .registerInstance("s", "other")
            .mutate {
                it.path("beans").type(mmType).action { c ->
                    c.create("b1", Bean)
                    c.create("sb1", SpecialBean)
                    c.withType(SpecialBean, SetProp)
                }
            }

        when:
        registry.atState("beans", ModelNode.State.SelfClosed)
        registry.get("beans.sb1", SpecialBean)
        registry.bindAllReferences()

        then:
        UnboundModelRulesException e = thrown()
        e.rules.size() == 1
        e.rules.first().mutableInputs.first().path == "beans.sb1.foo"
    }

    static class SetValue extends RuleSource {
        @Mutate
        void set(Bean bean) {
            bean.value = "changed"
        }
    }

    def "when targeting by type, can have rule use more general type than target"() {
        given:
        def mmType = ModelTypes.modelMap(Bean)

        registry
            .registerModelMap("beans", Bean) {
                it.registerFactory(Bean) { new Bean(name: it) }
                it.registerFactory(SpecialBean) { new SpecialBean(name: it) }
            }
            .registerInstance("s", "other")
            .mutate {
                it.path("beans").type(mmType).action { c ->
                    c.create("sb1", SpecialBean)
                    c.withType(SpecialBean, SetValue)
                }
            }

        when:
        registry.atState("beans", ModelNode.State.SelfClosed)

        then:
        registry.get("beans.sb1", SpecialBean).value == "changed"
    }

    def "when targeting by type, can have rule use more specific type than target"() {
        given:
        def mmType = ModelTypes.modelMap(Bean)

        registry
            .registerModelMap("beans", Bean) {
                it.registerFactory(Bean) { new Bean(name: it) }
                it.registerFactory(SpecialBean) { new SpecialBean(name: it) }
            }
            .registerInstance("s", "other")
            .mutate {
                it.path("beans").type(mmType).action { c ->
                    c.create("sb1", SpecialBean)
                    c.withType(Bean, SetOther)
                }
            }

        when:
        registry.atState("beans", ModelNode.State.SelfClosed)

        then:
        registry.get("beans.sb1", SpecialBean).other == "other"
    }

    def mockDeferredModelAction() {
        def action = Mock(DeferredModelAction)
        def descriptor = new SimpleModelRuleDescriptor("action")
        action.getDescriptor() >> descriptor
        return action
    }

}
