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

import org.gradle.model.entity.internal.NamedEntityInstantiator
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelState
import org.gradle.model.internal.core.ModelType
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.registry.DefaultInputs
import org.gradle.model.internal.registry.DefaultModelRegistry
import spock.lang.Specification

class DefaultCollectionBuilderTest extends Specification {

    def type = new ModelType<NamedThing>() {}

    class NamedThing {
        String name
        String other
    }

    class SpecialNamedThing extends NamedThing {

    }

    Map<String, NamedThing> data = [:]
    def instantiator = new NamedEntityInstantiator<NamedThing>() {
        @Override
        ModelType getType() {
            DefaultCollectionBuilderTest.this.type
        }

        @Override
        NamedThing create(String name) {
            def thing = new NamedThing(name: name)
            data.put(name, thing)
            thing
        }

        @Override
        def <S extends NamedThing> S create(String name, Class<S> type) {
            def thing = type.newInstance()
            thing.name = name
            data.put(name, thing)
            thing
        }
    }

    def containerPath = ModelPath.path("container")
    def registry = new DefaultModelRegistry()
    def builder = new DefaultCollectionBuilder<NamedThing>(
            containerPath,
            instantiator,
            new SimpleModelRuleDescriptor("builder"),
            new DefaultInputs([]),
            registry
    )


    def "simple create"() {
        when:
        builder.create("foo")

        then:
        registry.element(containerPath.child("foo")).adapter.asReadOnly(type).instance.name == "foo"
        data.foo.name == "foo"
    }

    def "does not eagerly create"() {
        when:
        builder.create("foo")
        builder.create("bar")

        then:
        registry.state(containerPath.child("foo")).status == ModelState.Status.PENDING
        registry.state(containerPath.child("bar")).status == ModelState.Status.PENDING
        data.isEmpty()

        when:
        registry.element(containerPath.child("bar"))

        then:
        data.bar.name == "bar"
    }

    def "registers as custom type"() {
        when:
        builder.create("foo", SpecialNamedThing)
        registry.element(containerPath.child("foo"))

        then:
        data.foo instanceof SpecialNamedThing
    }

    def "can register config rules"() {
        when:
        builder.create("foo") {
            it.other = "changed"
        }
        registry.element(containerPath.child("foo"))

        then:
        data.foo.other == "changed"
    }

    def "can register config rules for specified type"() {
        when:
        builder.create("foo", SpecialNamedThing) {
            it.other = "changed"
        }
        registry.element(containerPath.child("foo"))

        then:
        data.foo.other == "changed"
    }

}
