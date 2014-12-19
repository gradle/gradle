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

import org.gradle.api.Action
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.model.collection.CollectionBuilder
import org.gradle.model.internal.core.ActionBackedModelMutator
import org.gradle.model.internal.core.ModelCreators
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelReference
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
                        .withProjection(new PolymorphicDomainObjectContainerModelProjection<DefaultPolymorphicDomainObjectContainer<NamedThing>, NamedThing>(new DirectInstantiator(), container, NamedThing))
                        .simpleDescriptor("foo")
                        .build()
        )
        container.registerFactory(NamedThing) {
            NamedThing.newInstance(name: it)
        }
        container.registerFactory(SpecialNamedThing) { SpecialNamedThing.newInstance(name: it) }
    }

    void mutate(Action<? super CollectionBuilder<NamedThing>> action) {
        registry.mutate(new ActionBackedModelMutator<CollectionBuilder<NamedThing>>(
                ModelReference.of(containerPath, new ModelType<CollectionBuilder<NamedThing>>() {}),
                [],
                new SimpleModelRuleDescriptor("foo"),
                action
        ))
        registry.node(containerPath)
    }

    def "simple create"() {
        when:
        mutate { it.create("foo") }

        then:
        def childNode = registry.node(containerPath.child("foo"))
        childNode.adapter.asReadOnly(type, childNode, null).instance.name == "foo"
        container.getByName("foo")
    }

    @Ignore
    def "does not eagerly create"() {
        when:
        mutate {
            it.create("foo")
            it.create("bar")
        }

        then:
        container.isEmpty()

        when:
        registry.node(containerPath.child("bar"))

        then:
        container.getByName("bar")
    }

    def "registers as custom type"() {
        when:
        mutate { it.create("foo", SpecialNamedThing) }
        registry.node(containerPath.child("foo"))

        then:
        container.getByName("foo") instanceof SpecialNamedThing
    }

    def "can register config rules"() {
        when:
        mutate {
            it.create("foo") {
                it.other = "changed"
            }
        }
        registry.node(containerPath.child("foo"))

        then:
        container.getByName("foo").other == "changed"
    }

    def "can register config rules for specified type"() {
        when:
        mutate {
            it.create("foo", SpecialNamedThing) {
                it.other = "changed"
            }
        }
        registry.node(containerPath.child("foo"))

        then:
        container.getByName("foo").other == "changed"
    }

}
