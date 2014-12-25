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

    class SpecialNamedThing extends NamedThing {

    }

    def containerPath = ModelPath.path("container")
    def registry = new DefaultModelRegistry()
    def container = new DefaultPolymorphicDomainObjectContainer<NamedThing>(NamedThing, new DirectInstantiator(), { it.getName() })

    def setup() {
        registry.create(
                ModelCreators.bridgedInstance(
                        ModelReference.of(containerPath, new ModelType<PolymorphicDomainObjectContainer<NamedThing>>() {}),
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
        def mutator = Stub(ModelMutator)
        mutator.subject >> ModelReference.of(containerPath, new ModelType<CollectionBuilder<NamedThing>>() {})
        mutator.descriptor >> new SimpleModelRuleDescriptor("foo")
        mutator.mutate(_, _, _) >> { new ClosureBackedAction<NamedThing>(action).execute(it[1]) }

        registry.mutate(MutationType.Mutate, mutator)
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

    def "can register config rules"() {
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

    def "can register config rules for specified type"() {
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

    def "can register mutate rule for items"() {
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
            finalizeAll {
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
