/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal

import org.gradle.api.Named
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.model.collection.CollectionBuilder
import org.gradle.model.internal.core.*
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.registry.DefaultInputs
import org.gradle.model.internal.registry.DefaultModelRegistry
import spock.lang.Specification

class PolymorphicDomainObjectContainerModelCreatorTest extends Specification {

    static class NamedThing implements Named {
        final String name
        String other

        NamedThing(String name) {
            this.name = name
        }
    }

    static class SpecialNamedThing extends NamedThing {
        SpecialNamedThing(String name) {
            super(name)
        }
        String special
    }

    static class ThingContainer extends DefaultPolymorphicDomainObjectContainer<NamedThing> {
        ThingContainer(Class<NamedThing> type, Instantiator instantiator) {
            super(type, instantiator)
        }
    }

    def container = new ThingContainer(NamedThing, new DirectInstantiator()) {
        {
            registerFactory(NamedThing) {
                new NamedThing(it)
            }
        }
    }

    def builderType = new ModelType<CollectionBuilder<NamedThing>>() {}
    def subBuilderType = new ModelType<CollectionBuilder<SpecialNamedThing>>() {}


    def registry = new DefaultModelRegistry()
    def reference = ModelReference.of("container", ThingContainer)
    def creator = ModelCreators.of(reference, container)
            .simpleDescriptor("container")
            .withProjection(new PolymorphicDomainObjectContainerModelProjection(container, NamedThing))
            .build()
    def adapter = creator.create(new DefaultInputs([]))

    def setup() {
        registry.create(creator)
    }

    private registerSpecialCreator() {
        container.registerFactory(SpecialNamedThing) {
            new SpecialNamedThing(it)
        }
    }

    def "can view as read types"() {
        expect:
        adapter.asReadOnly(ModelType.of(ThingContainer)).instance.is(container)
        adapter.asReadOnly(builderType) == null // only a writable type
    }

    def "can view as write types"() {
        expect:
        adapter.asWritable(ModelBinding.of(reference), new SimpleModelRuleDescriptor("write"), new DefaultInputs([]), registry).instance.is(container)
        asBuilder().instance instanceof CollectionBuilder
        asSubBuilder().instance instanceof CollectionBuilder
    }

    def "can create items"() {
        // don't need to test too much here, assume that DefaultCollectionBuilder is used internally
        given:
        registerSpecialCreator()
        def builder = asBuilder().instance

        when:
        builder.create("foo") {
            it.other = "foo-changed"
        }

        builder.create("bar", SpecialNamedThing) {
            it.other = "foo-changed"
        }

        then:
        registry.get(reference.path.child("foo"), ModelType.of(NamedThing)).name == "foo"

        def bar = registry.get(reference.path.child("bar"), ModelType.of(NamedThing))
        bar.name == "bar"
        bar instanceof SpecialNamedThing
    }

    def "can create subtyped items"() {
        given:
        registerSpecialCreator()
        def builder = asSubBuilder().instance

        when:
        builder.create("foo") {
            it.other = "foo-changed"
            it.special = "special-changed"
        }

        then:
        registry.get(reference.path.child("foo"), ModelType.of(SpecialNamedThing)).special == "special-changed"
    }

    def "promise describes possible types"() {
        when:
        def promise = creator.promise

        then:
        promise.readableTypeDescriptions.toList() == [IdentityModelProjection.description(ModelType.of(ThingContainer))]
        promise.writableTypeDescriptions.toList() == [
                IdentityModelProjection.description(ModelType.of(ThingContainer)),
                PolymorphicDomainObjectContainerModelProjection.getBuilderTypeDescriptionForCreatableTypes([NamedThing])
        ]

        when:
        registerSpecialCreator()

        then:
        promise.readableTypeDescriptions.toList() == [IdentityModelProjection.description(ModelType.of(ThingContainer))]
        promise.writableTypeDescriptions.toList() == [
                IdentityModelProjection.description(ModelType.of(ThingContainer)),
                PolymorphicDomainObjectContainerModelProjection.getBuilderTypeDescriptionForCreatableTypes([NamedThing, SpecialNamedThing])
        ]
    }

    def ModelView<? extends CollectionBuilder<SpecialNamedThing>> asSubBuilder() {
        adapter.asWritable(ModelBinding.of(ModelReference.of(reference.path, subBuilderType)), new SimpleModelRuleDescriptor("write"), new DefaultInputs([]), registry)
    }

    def ModelView<? extends CollectionBuilder<NamedThing>> asBuilder() {
        adapter.asWritable(ModelBinding.of(ModelReference.of(reference.path, builderType)), new SimpleModelRuleDescriptor("write"), new DefaultInputs([]), registry)
    }
}
