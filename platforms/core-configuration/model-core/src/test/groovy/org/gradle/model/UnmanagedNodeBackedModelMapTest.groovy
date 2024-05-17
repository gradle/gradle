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
import org.gradle.api.reflect.ObjectInstantiationException
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

    abstract class NonConstructibleNamedThing extends NamedThing {
        NonConstructibleNamedThing(String name) {
            super(name)
        }
    }

    def "reasonable error message when creating a non-constructible type"() {
        when:
        mutate {
            create("foo", NonConstructibleNamedThing)
        }
        realize()

        then:
        def e = thrown ModelRuleExecutionException
        e.cause instanceof ObjectInstantiationException
        e.cause.message == "Could not create an instance of type $NonConstructibleNamedThing.name."
    }

}
