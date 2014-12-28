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
        registry.node(containerPath)
    }

    def "can define an item with name"() {
        when:
        mutate { create("foo") }

        then:
        container.getByName("foo") != null
        registry.get(containerPath.child("foo"), ModelType.of(NamedThing)) == container.getByName("foo")
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
        registry.node(containerPath.child("bar"))

        then:
        container.getByName("bar")
    }

    def "can define item with custom type"() {
        when:
        mutate { create("foo", SpecialNamedThing) }
        registry.node(containerPath.child("foo"))

        then:
        container.getByName("foo") instanceof SpecialNamedThing
    }

    def "can define item using filtered collection"() {
        when:
        mutate {
            withType(SpecialNamedThing).create("foo")
            withType(NamedThing).create("bar")
        }
        registry.node(containerPath.child("foo"))
        registry.node(containerPath.child("bar"))

        then:
        container.getByName("foo") instanceof SpecialNamedThing
        container.getByName("bar").class == NamedThing
    }

    def "fails when using filtered collection to define item of type that is not assignable to collection item type"() {
        when:
        mutate {
            withType(String).create("foo")
        }
        registry.node(containerPath.child("foo"))

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
        registry.node(containerPath.child("foo"))

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
        registry.node(containerPath.child("foo"))

        then:
        container.getByName("foo").other == "changed"
    }

    def "can query collection size"() {
        when:
        mutate {
            create("a")
            create("b")
            assert size() == 2
        }

        then:
        container.size() == 2
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
        }

        then:
        container.size() == 2
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
        registry.node(containerPath.child("foo"))

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
        registry.node(containerPath.child("foo"))

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
        registry.node(containerPath.child("foo"))

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
        registry.node(containerPath.child("foo"))
        registry.node(containerPath.child("bar"))

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
        registry.node(containerPath.child("foo"))

        then:
        container.getByName("foo").other == "changed"
    }

    def "can register mutate rule for all items with specific type"() {
        when:
        mutate {
            withType(SpecialNamedThing) {
                assert other == "original"
                other = "changed"
            }
            create("foo") {
                other = "original"
            }
            create("bar", SpecialNamedThing) {
                other = "original"
            }
        }
        registry.node(containerPath.child("foo"))

        then:
        container.getByName("foo").other == "original"
        container.getByName("bar").other == "changed"
    }

    def "can register finalize rule for all items"() {
        when:
        mutate {
            afterEach {
                assert other == "changed"
                other = "finalized"
            }
            all {
                assert other == "original"
                other = "changed"
            }
            create("foo") {
                other = "original"
            }
        }
        registry.node(containerPath.child("foo"))

        then:
        container.getByName("foo").other == "finalized"
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
        registry.node(containerPath.child("foo"))

        then:
        container.getByName("foo").other == "changed"
        container.getByName("bar") instanceof SpecialNamedThing
    }
}
