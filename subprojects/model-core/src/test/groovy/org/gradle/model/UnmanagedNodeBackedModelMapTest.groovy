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

package org.gradle.model
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Namer
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer
import org.gradle.api.internal.rules.RuleAwareNamedDomainObjectFactoryRegistry
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.reflect.ObjectInstantiationException
import org.gradle.model.collection.internal.ChildNodeInitializerStrategyAccessors
import org.gradle.model.collection.internal.ModelMapModelProjection
import org.gradle.model.internal.core.*
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor
import org.gradle.model.internal.type.ModelType

class UnmanagedNodeBackedModelMapTest extends NodeBackedModelMapSpec<NamedThing, SpecialNamedThing> {

    static class NamedThing implements Named {
        String name
        String other

        NamedThing(String name) {
            this.name = name
        }
    }

    static class SpecialNamedThing extends NamedThing implements Special {
        SpecialNamedThing(String name) {
            super(name)
        }
    }

    static class Container<T> extends DefaultPolymorphicDomainObjectContainer<T> implements RuleAwareNamedDomainObjectFactoryRegistry<T>, NamedEntityInstantiator<T> {
        Container(Class<T> type, Instantiator instantiator, Namer<? super T> namer) {
            super(type, instantiator, namer)
        }

        @Override
        <U extends T> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory, ModelRuleDescriptor descriptor) {
            registerFactory(type, factory)
        }
    }

    Class<NamedThing> itemClass = NamedThing
    Class<SpecialNamedThing> specialItemClass = SpecialNamedThing

    def setup() {
        registry.register(
            ModelRegistrations.bridgedInstance(
                ModelReference.of(path, new ModelType<NamedEntityInstantiator<NamedThing>>() {}),
                { name, type -> DirectInstantiator.instantiate(type, name) } as NamedEntityInstantiator
            )
                .descriptor(path.toString())
                .withProjection(ModelMapModelProjection.unmanaged(
                    itemType,
                    ChildNodeInitializerStrategyAccessors.of(NodeBackedModelMap.createUsingParentNode(itemType)))
                )
                .build()
        )
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
        realizeAsModelMap().size() == 2
    }

    def "can query filtered collection size"() {
        when:
        mutate {
            create("a")
            create("b", SpecialNamedThing)

            assert withType(SpecialNamedThing).size() == 1
            assert withType(Special).size() == 1
            assert withType(NamedThing).size() == 2
            assert withType(String).size() == 0

            assert !withType(SpecialNamedThing).isEmpty()
            assert withType(String).isEmpty()
        }

        then:
        realizeAsModelMap().withType(SpecialNamedThing).size() == 1
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
        realizeAsModelMap().containsKey("a")
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
        realizeAsModelMap().withType(SpecialNamedThing).containsKey("b")
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
        realizeAsModelMap().keySet() as List == ["a", "b"]
    }

    def "can query filtered collection keys"() {
        when:
        mutate {
            assert withType(NamedThing).keySet().isEmpty()
            assert withType(String).keySet().isEmpty()

            create("b", SpecialNamedThing)
            create("a")

            assert withType(NamedThing).keySet() as List == ["a", "b"]
            assert withType(SpecialNamedThing).keySet() as List == ["b"]
            assert withType(Special).keySet() as List == ["b"]
            assert withType(String).keySet().isEmpty()
        }

        then:
        realizeAsModelMap().withType(Special).keySet() as List == ["b"]
    }

    def "reasonable error message when creating a non-constructable type"() {
        when:
        mutate { create("foo", List) }
        realize()

        then:
        def e = thrown ModelRuleExecutionException
        e.cause instanceof ObjectInstantiationException
        e.cause.message == "Could not create an instance of type java.util.List."
    }

}
