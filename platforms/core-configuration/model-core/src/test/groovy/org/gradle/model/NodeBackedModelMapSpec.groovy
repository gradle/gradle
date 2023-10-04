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
import org.gradle.internal.BiAction
import org.gradle.model.internal.core.DeferredModelAction
import org.gradle.model.internal.core.ModelActionRole
import org.gradle.model.internal.core.ModelNode
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.model.internal.core.NodeBackedModelMap
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.fixture.ProjectRegistrySpec
import org.gradle.model.internal.manage.instance.ManagedInstance
import org.gradle.model.internal.registry.UnboundModelRulesException
import org.gradle.model.internal.type.ModelType
import org.gradle.model.internal.type.ModelTypes
import org.gradle.util.internal.ClosureBackedAction

import static org.gradle.model.ModelTypeTesting.fullyQualifiedNameOf
import static org.gradle.util.internal.TextUtil.normaliseLineSeparators

abstract class NodeBackedModelMapSpec<T extends Named, S extends Special & T> extends ProjectRegistrySpec {
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

    def "can query collection size"() {
        when:
        mutate {
            create("a")
            create("b")
        }

        then:
        realizeAsModelMap().size() == 2
        !realizeAsModelMap().isEmpty()
    }

    def "can query filtered collection size"() {
        when:
        mutateWithoutDelegation() {
            it.create("a")
            it.create("b", specialItemClass)
        }

        then:
        with(realizeAsModelMap()) {
            assert withType(this.specialItemClass).size() == 1
            assert withType(Special).size() == 1
            assert withType(this.itemClass).size() == 2
            assert withType(String).size() == 0

            assert !withType(this.specialItemClass).isEmpty()
            assert withType(String).isEmpty()
        }
    }

    def "can query collection membership"() {
        when:
        mutate {
            create("a")
            create("b")
        }

        then:
        realizeAsModelMap().containsKey("a")
        realizeAsModelMap().containsKey("b")
        !realizeAsModelMap().containsKey("c")
    }

    def "can query filtered collection membership"() {
        when:
        mutateWithoutDelegation() {
            it.create("a")
            it.create("b", specialItemClass)
        }

        then:
        with(realizeAsModelMap()) {
            withType(this.specialItemClass).containsKey("b")
            withType(Object).containsKey("a")
            withType(this.itemClass).containsKey("a")
            !withType(this.specialItemClass).containsKey("a")
            !withType(Special).containsKey("a")
            !withType(String).containsKey("a")

            withType(Object).containsKey("b")
            withType(this.itemClass).containsKey("b")
            withType(this.specialItemClass).containsKey("b")
            withType(Special).containsKey("b")
            !withType(String).containsKey("b")
        }
    }

    def "can query collection keys"() {
        when:
        mutate {
            create("a")
            create("b")
        }

        then:
        realizeAsModelMap().keySet() as List == ["a", "b"]
    }

    def "can query filtered collection keys"() {
        when:
        mutateWithoutDelegation() {
            it.create("b", specialItemClass)
            it.create("a")
        }

        then:
        with(realizeAsModelMap()) {
            assert withType(Special).keySet() as List == ["b"]
            assert withType(this.itemClass).keySet() as List == ["a", "b"]
            assert withType(this.specialItemClass).keySet() as List == ["b"]
            assert withType(Special).keySet() as List == ["b"]
            assert withType(String).keySet().isEmpty()
        }
    }

    def "withType() returns same instance when element type is the same"() {
        mutateWithoutDelegation {
            it.create("item", itemClass)
            it.create("specialItem", specialItemClass)
        }
        def map = realizeAsModelMap()
        expect:
        map.is(map.withType(itemClass))
    }

    def "withType() filtering is additive"() {
        mutateWithoutDelegation {
            it.create("item", itemClass)
            it.create("specialItem", specialItemClass)
        }
        def map = realizeAsModelMap()

        expect:
        with (map.withType(itemClass)) {
            it*.name == ["item", "specialItem"]
            it.keySet() as List == ["item", "specialItem"]
            it.size() == 2
            !it.isEmpty()
            this.itemClass.isInstance it.get("item")
            this.specialItemClass.isInstance it.get("specialItem")
        }

        with (map.withType(specialItemClass).withType(Named)) {
            it*.name == ["specialItem"]
            it.keySet() as List == ["specialItem"]
            it.size() == 1
            !it.isEmpty()
            it.get("item") == null
            this.specialItemClass.isInstance it.get("specialItem")
        }

        with (map.withType(specialItemClass).withType(itemClass)) {
            it*.name == ["specialItem"]
            it.keySet() as List == ["specialItem"]
            it.size() == 1
            !it.isEmpty()
            it.get("item") == null
            this.specialItemClass.isInstance it.get("specialItem")
        }

        with (map.withType(String).withType(itemClass)) {
            it*.name == []
            it.keySet().isEmpty()
            it.size() == 0
            it.isEmpty()
            it.get("item") == null
            it.get("specialItem") == null
        }
    }

    def "all(Action) respects chained filtering"() {
        expect:
        accessedBy { map, action -> map.withType(specialItemClass).all(action) } == ["specialItem"]
    }

    def "all(DeferredModelAction) respects chained filtering"() {
        expect:
        accessedByDeferred { map, action -> ((NodeBackedModelMap) map.withType(specialItemClass)).all(action) } == ["specialItem@Initialize"]
    }

    def "beforeEach(Action) respects chained filtering"() {
        expect:
        accessedBy { map, action -> map.withType(specialItemClass).beforeEach(action) } == ["specialItem"]
    }

    def "beforeEach(Class, Action) respects chained filtering"() {
        expect:
        accessedBy { map, action -> map.withType(specialItemClass).beforeEach(Object, action) } == ["specialItem"]
    }

    def "beforeEach(DeferredModelAction) respects chained filtering"() {
        expect:
        accessedByDeferred { map, action -> ((NodeBackedModelMap) map.withType(specialItemClass)).beforeEach(action) } == ["specialItem@Defaults"]
    }

    def "beforeEach(Class, DeferredModelAction) respects chained filtering"() {
        expect:
        accessedByDeferred { map, action -> ((NodeBackedModelMap) map.withType(specialItemClass)).beforeEach(Object, action) } == ["specialItem@Defaults"]
    }

    def "afterEach(Action) respects chained filtering"() {
        expect:
        accessedBy { map, action -> map.withType(specialItemClass).afterEach(action) } == ["specialItem"]
    }

    def "afterEach(Class, Action) respects chained filtering"() {
        expect:
        accessedBy { map, action -> map.withType(specialItemClass).afterEach(Object, action) } == ["specialItem"]
    }

    def "afterEach(DeferredModelAction) respects chained filtering"() {
        expect:
        accessedByDeferred { map, action -> ((NodeBackedModelMap) map.withType(specialItemClass)).afterEach(action) } == ["specialItem@Finalize"]
    }

    def "afterEach(Class, DeferredModelAction) respects chained filtering"() {
        expect:
        accessedByDeferred { map, action -> ((NodeBackedModelMap) map.withType(specialItemClass)).afterEach(Object, action) } == ["specialItem@Finalize"]
    }

    def "named(String, Action) fails when named element requested in filtered collection with incompatible type"() {
        when:
        accessedBy { map, action -> map.withType(specialItemClass).named("item", action) }

        then:
        def ex = thrown InvalidModelRuleException
        ex.cause instanceof ModelRuleBindingException
        normaliseLineSeparators(ex.cause.message) == incompatibleTypesMessage()
    }

    def "named(String, Action) fails when named element requested in chain filtered collection with incompatible type"() {
        when:
        accessedBy { map, action -> map.withType(specialItemClass).withType(itemClass).named("item", action) }

        then:
        def ex = thrown ModelRuleExecutionException
        ex.cause instanceof InvalidModelRuleException
        ex.cause.cause instanceof ModelRuleBindingException
        normaliseLineSeparators(ex.cause.cause.message) == incompatibleTypesMessage()
    }

    private String incompatibleTypesMessage() {
        """Model reference to element 'map.item' with type ${fullyQualifiedNameOf(specialItemClass)} is invalid due to incompatible types.
This element was created by testrule > create(item) and can be mutated as the following types:
  - ${fullyQualifiedNameOf(itemClass)} (or assignment compatible type thereof)
  - ${ModelElement.name} (or assignment compatible type thereof)"""
    }

    static class NamedRules extends RuleSource {}

    def "named(String, RuleSource) fails when named element requested in chain filtered collection with incompatible type"() {
        when:
        accessedBy { map, action -> map.withType(specialItemClass).withType(itemClass).named("item", NamedRules) }

        then:
        def ex = thrown ModelRuleExecutionException
        ex.cause instanceof InvalidModelRuleException
        ex.cause.cause instanceof ModelRuleBindingException
        normaliseLineSeparators(ex.cause.cause.message) == incompatibleTypesMessage()
    }

    def "named(String, DeferredModelAction) fails when named element requested in filtered collection with incompatible type"() {
        when:
        accessedByDeferred() { map, action -> ((NodeBackedModelMap) map.withType(specialItemClass)).named("item", action) }

        then:
        def ex = thrown InvalidModelRuleException
        ex.cause instanceof ModelRuleBindingException
        normaliseLineSeparators(ex.cause.message) == incompatibleTypesMessage()
    }

    def "named(String, DeferredModelAction) fails when named element requested in chain filtered collection with incompatible type"() {
        when:
        accessedByDeferred() { map, action -> ((NodeBackedModelMap) map.withType(specialItemClass).withType(itemClass)).named("item", action) }

        then:
        def ex = thrown ModelRuleExecutionException
        ex.cause instanceof InvalidModelRuleException
        ex.cause.cause instanceof ModelRuleBindingException
        normaliseLineSeparators(ex.cause.cause.message) == incompatibleTypesMessage()
    }

    def "withType(Class, Action) respects chained filtering"() {
        expect:
        accessedBy { map, action -> map.withType(specialItemClass).withType(Object, action) } == ["specialItem"]
    }

    def "withType(Class, DeferredModelAction) respects chained filtering"() {
        expect:
        accessedByDeferred { map, action -> ((NodeBackedModelMap) map.withType(specialItemClass)).withType(Object, action) } == ["specialItem@Mutate"]
    }

    def "withType(Class, RuleSource) respects chained filtering"() {
        def withTypeRules = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.*

            class WithTypeRules extends RuleSource {
                static final List<String> accessed = []

                @Mutate
                void mutateSpecial($itemClass.name item) {
                    accessed.add(item.name)
                }
            }
        """

        mutateWithoutDelegation {
            it.create("item", itemClass)
            it.create("specialItem", specialItemClass)
            it.withType(specialItemClass).withType(Object, withTypeRules)
        }
        realizeAsModelMap()

        expect:
        withTypeRules.accessed == ["specialItem"]
    }

    def accessedBy(BiAction<ModelMap, Action<? super Named>> mutator) {
        def accessed = []
        mutateWithoutDelegation {
            it.create("item", itemClass)
            it.create("specialItem", specialItemClass)
            mutator.execute(it) { item -> accessed += item.name }
        }
        realizeAsModelMap()
        return accessed
    }

    def accessedByDeferred(BiAction<NodeBackedModelMap, DeferredModelAction> mutator) {
        def accessed = []
        def action = Mock(DeferredModelAction) {
            getDescriptor() >> new SimpleModelRuleDescriptor("deferred")
            execute(_, _) >> { MutableModelNode node, ModelActionRole role ->
                accessed += node.getPath().getName() + "@" + role
            }
        }
        mutateWithoutDelegation {
            it.create("item", itemClass)
            it.create("specialItem", specialItemClass)
            mutator.execute((NodeBackedModelMap) it, action)
        }
        realizeAsModelMap()
        return accessed
    }

    def "create(String, Class) respects collection type"() {
        mutate {
            create("item", String)
        }
        when:
        realize()
        then:
        def ex = thrown ModelRuleExecutionException
        assertCannotCreateException ex
    }

    def "create(String, Class, Action) respects collection type"() {
        mutate {
            create("item", String, Mock(Action))
        }
        when:
        realize()
        then:
        def ex = thrown ModelRuleExecutionException
        assertCannotCreateException ex
    }

    def "create(String, Class, DeferredModelAction) respects collection type"() {
        def action = Mock(DeferredModelAction) {
            getDescriptor() >> new SimpleModelRuleDescriptor("deferred")
        }
        mutateWithoutDelegation() {
            ((NodeBackedModelMap) it).create("item", String, action)
        }
        when:
        realize()
        then:
        def ex = thrown ModelRuleExecutionException
        assertCannotCreateException ex
    }

    def "fails when using filtered collection to define item of type that is not assignable to collection item type"() {
        mutate {
            withType(String).create("item")
        }
        when:
        realize()
        then:
        def ex = thrown ModelRuleExecutionException
        assertCannotCreateException ex
    }

    def assertCannotCreateException(ModelRuleExecutionException ex) {
        assert ex.cause instanceof IllegalArgumentException
        assert ex.cause.message == "Cannot create 'map.item' with type '$String.name' as this is not a subtype of '${fullyQualifiedNameOf(itemClass)}'."
        return true
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
        thrown ReadOnlyModelViewException
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
        e.cause.message.startsWith('''Type java.lang.Object is not a valid rule source:
- Rule source classes must directly extend org.gradle.model.RuleSource''')
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
                    c.named("missingElement", ElementRules)
                }
            }

        when:
        registry.atState(ModelPath.path("beans"), ModelNode.State.SelfClosed)
        registry.bindAllReferences()

        then:
        UnboundModelRulesException e = thrown()
        normaliseLineSeparators(e.message).contains("""
  testrule > named(missingElement, $ElementRules.name)
    subject:
      - beans.missingElement NodeBackedModelMapSpec.Bean [*]
""")
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
