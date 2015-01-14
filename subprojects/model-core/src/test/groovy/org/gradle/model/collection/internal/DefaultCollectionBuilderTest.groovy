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

package org.gradle.model.collection.internal

import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.api.internal.ClosureBackedAction
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.model.InvalidModelRuleException
import org.gradle.model.ModelRuleBindingException
import org.gradle.model.collection.CollectionBuilder
import org.gradle.model.internal.core.*
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.registry.DefaultModelRegistry
import org.gradle.model.internal.type.ModelType
import spock.lang.Ignore
import spock.lang.Specification

class DefaultCollectionBuilderTest extends Specification {

    def type = new ModelType<NamedThing>() {}

    class NamedThing {
        String name
        String other
    }

    interface Special { }

    class SpecialNamedThing extends NamedThing implements Special {
    }

    def containerPath = ModelPath.path("container")
    def containerType = new ModelType<PolymorphicDomainObjectContainer<NamedThing>>() {}
    def collectionBuilderType = new ModelType<CollectionBuilder<NamedThing>>() {}
    def registry = new DefaultModelRegistry()
    def container = new DefaultPolymorphicDomainObjectContainer<NamedThing>(NamedThing, new DirectInstantiator(), { it.getName() })

    def setup() {
        registry.create(
                ModelCreators.bridgedInstance(
                        ModelReference.of(containerPath, containerType),
                        container
                )
                        .withProjection(new PolymorphicDomainObjectContainerModelProjection<DefaultPolymorphicDomainObjectContainer<NamedThing>, NamedThing>(container, NamedThing))
                        .simpleDescriptor("foo")
                        .build()
        )
        container.registerFactory(NamedThing) {
            NamedThing.newInstance(name: it)
        }
        container.registerFactory(SpecialNamedThing) { SpecialNamedThing.newInstance(name: it) }
    }

    void mutate(@DelegatesTo(CollectionBuilder) Closure<? super CollectionBuilder<NamedThing>> action) {
        def mutator = Stub(ModelAction)
        mutator.subject >> ModelReference.of(containerPath, new ModelType<CollectionBuilder<NamedThing>>() {})
        mutator.descriptor >> new SimpleModelRuleDescriptor("foo")
        mutator.execute(_, _, _) >> { new ClosureBackedAction<NamedThing>(action).execute(it[1]) }

        registry.apply(ModelActionRole.Mutate, mutator)
        registry.realizeNode(containerPath)
    }

    def "can define an item with name"() {
        when:
        mutate { create("foo") }

        then:
        container.getByName("foo") != null
        registry.realize(containerPath.child("foo"), ModelType.of(NamedThing)) == container.getByName("foo")
    }

    @Ignore
    def "does not eagerly create item"() {
        when:
        mutate {
            create("foo")
            create("bar")
        }

        then:
        container.isEmpty()

        when:
        registry.realizeNode(containerPath.child("bar"))

        then:
        container.getByName("bar")
    }

    def "can define item with custom type"() {
        when:
        mutate { create("foo", SpecialNamedThing) }
        registry.realizeNode(containerPath.child("foo"))

        then:
        container.getByName("foo") instanceof SpecialNamedThing
    }

    def "can define item using filtered collection"() {
        when:
        mutate {
            withType(SpecialNamedThing).create("foo")
            withType(NamedThing).create("bar")
        }
        registry.realizeNode(containerPath.child("foo"))
        registry.realizeNode(containerPath.child("bar"))

        then:
        container.getByName("foo") instanceof SpecialNamedThing
        container.getByName("bar").class == NamedThing
    }

    def "fails when using filtered collection to define item of type that is not assignable to collection item type"() {
        when:
        mutate {
            withType(String).create("foo")
        }
        registry.realizeNode(containerPath.child("foo"))

        then:
        ModelRuleExecutionException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Cannot create an item of type java.lang.String as this is not a subtype of $NamedThing.name."
    }

    def "can register config rules for item"() {
        when:
        mutate {
            create("foo") {
                other = "changed"
            }
        }
        registry.realizeNode(containerPath.child("foo"))

        then:
        container.getByName("foo").other == "changed"
    }

    def "can register config rule and type for item"() {
        when:
        mutate {
            create("foo", SpecialNamedThing) {
                other = "changed"
            }
        }
        registry.realizeNode(containerPath.child("foo"))

        then:
        container.getByName("foo").other == "changed"
    }

    def "can query collection size"() {
        when:
        mutate {
            assert size() == 0
            assert it.isEmpty()

            create("a")
            create("b")

            assert size() == 2
            assert !isEmpty()
        }

        then:
        registry.realize(containerPath, collectionBuilderType).size() == 2
    }

    def "can query filtered collection size"() {
        when:
        mutate {
            create("a")
            create("b", SpecialNamedThing)

            assert withType(SpecialNamedThing).size() == 1
            assert withType(Special).size() == 1
            assert withType(NamedThing).size() == 2
            assert withType(Object).size() == 2
            assert withType(String).size() == 0

            assert !withType(SpecialNamedThing).isEmpty()
            assert withType(String).isEmpty()
        }

        then:
        registry.realize(containerPath, collectionBuilderType).withType(SpecialNamedThing).size() == 1
    }

    def "can query collection membership"() {
        when:
        mutate {
            assert !containsKey("a")
            assert !containsKey(12)

            create("a")
            create("b")

            assert it.containsKey("a")
        }

        then:
        registry.realize(containerPath, collectionBuilderType).containsKey("a")
    }

    def "can query filtered collection membership"() {
        when:
        mutate {
            assert !withType(NamedThing).containsKey("a")
            assert !withType(Integer).containsKey(12)

            create("a")
            create("b", SpecialNamedThing)

            assert withType(Object).containsKey("a")
            assert withType(NamedThing).containsKey("a")
            assert !withType(SpecialNamedThing).containsKey("a")
            assert !withType(Special).containsKey("a")
            assert !withType(String).containsKey("a")

            assert withType(Object).containsKey("b")
            assert withType(NamedThing).containsKey("b")
            assert withType(SpecialNamedThing).containsKey("b")
            assert withType(Special).containsKey("b")
            assert !withType(String).containsKey("b")
        }

        then:
        registry.realize(containerPath, collectionBuilderType).withType(SpecialNamedThing).containsKey("b")
    }

    def "can query collection keys"() {
        when:
        mutate {
            assert keySet().isEmpty()

            create("a")
            create("b")

            assert keySet() as List == ["a", "b"]
        }

        then:
        registry.realize(containerPath, collectionBuilderType).keySet() as List == ["a", "b"]
    }

    def "can query filtered collection keys"() {
        when:
        mutate {
            assert withType(Object).keySet().isEmpty()
            assert withType(NamedThing).keySet().isEmpty()
            assert withType(String).keySet().isEmpty()

            create("b", SpecialNamedThing)
            create("a")

            assert withType(Object).keySet() as List == ["a", "b"]
            assert withType(NamedThing).keySet() as List == ["a", "b"]
            assert withType(SpecialNamedThing).keySet() as List == ["b"]
            assert withType(Special).keySet() as List == ["b"]
            assert withType(String).keySet().isEmpty()
        }

        then:
        registry.realize(containerPath, collectionBuilderType).withType(Special).keySet() as List == ["b"]
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
        registry.realizeNode(containerPath.child("foo"))

        then:
        container.getByName("foo").other == "changed"
    }

    def "can register mutate rule for item with name using filtered container"() {
        when:
        mutate {
            withType(Object).named("foo") {
                other += " Object"
            }
            withType(Special).named("foo") {
                other += " Special"
            }
            withType(SpecialNamedThing).named("foo") {
                other += " SpecialNamedThing"
            }
            create("foo", SpecialNamedThing) {
                other = "types:"
            }
        }
        registry.realizeNode(containerPath.child("foo"))

        then:
        container.getByName("foo").other == "types: Object Special SpecialNamedThing"
    }

    def "fails when named item does not have view with appropriate type"() {
        when:
        mutate {
            withType(String).named("foo") {
            }
            create("foo")
        }
        registry.realizeNode(containerPath.child("foo"))

        then:
        ModelRuleExecutionException e = thrown()
        e.cause instanceof InvalidModelRuleException
        e.cause.cause instanceof ModelRuleBindingException
        e.cause.cause.message.startsWith("Model reference to element 'container.foo' with type java.lang.String is invalid due to incompatible types.")
    }

    def "can register mutate rule for all items using filtered container"() {
        when:
        mutate {
            withType(Object).all {
                other += " Object"
            }
            withType(String).all {
                other += " String"
            }
            withType(NamedThing).all {
                other += " NamedThing"
            }
            withType(Special).all {
                other += " Special"
            }
            withType(SpecialNamedThing).all {
                other += " SpecialNamedThing"
            }
            create("foo") {
                other = "types:"
            }
            create("bar", SpecialNamedThing) {
                other = "types:"
            }
        }
        registry.realizeNode(containerPath.child("foo"))
        registry.realizeNode(containerPath.child("bar"))

        then:
        container.getByName("foo").other == "types: Object NamedThing"
        container.getByName("bar").other == "types: Object NamedThing Special SpecialNamedThing"
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
        registry.realizeNode(containerPath.child("foo"))

        then:
        container.getByName("foo").other == "changed"
    }

    def "can register mutate rule for all items with specific type"() {
        when:
        mutate {
            withType(Object) {
                other += " Object"
            }
            withType(String) {
                other += " String"
            }
            withType(Special) {
                other += " Special"
            }
            withType(SpecialNamedThing) {
                other += " SpecialNamedThing"
            }
            create("foo") {
                other = "foo:"
            }
            create("bar", SpecialNamedThing) {
                other = "bar:"
            }
        }
        registry.realizeNode(containerPath.child("foo"))

        then:
        container.getByName("foo").other == "foo: Object"
        container.getByName("bar").other == "bar: Object Special SpecialNamedThing"
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
        registry.realizeNode(containerPath.child("foo"))

        then:
        container.getByName("foo").other == "beforeEach{} create() all{}"
    }

    def "can register defaults rule for all items with type"() {
        when:
        mutate {
            beforeEach(Object) {
                other = "Object"
            }
            beforeEach(String) {
                other += " String"
            }
            beforeEach(Special) {
                other += " Special"
            }
            beforeEach(SpecialNamedThing) {
                other += " SpecialNamedThing"
            }
            create("foo") {
                other += " create(foo)"
            }
            create("bar", SpecialNamedThing) {
                other += " create(bar)"
            }
        }
        registry.realizeNode(containerPath.child("foo"))
        registry.realizeNode(containerPath.child("bar"))

        then:
        container.getByName("foo").other == "Object create(foo)"
        container.getByName("bar").other == "Object Special SpecialNamedThing create(bar)"
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
        registry.realizeNode(containerPath.child("foo"))

        then:
        container.getByName("foo").other == "create() all{} afterEach{}"
    }

    def "provides groovy DSL"() {
        when:
        mutate {
            foo {
                assert other == "original"
                other = "changed"
            }
            foo(NamedThing) {
                other = "original"
            }
            bar(SpecialNamedThing)
        }
        registry.realizeNode(containerPath.child("foo"))

        then:
        container.getByName("foo").other == "changed"
        container.getByName("bar") instanceof SpecialNamedThing
    }
}
