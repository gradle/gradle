/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.attributes

import org.gradle.api.Describable
import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.util.AttributeTestUtil
import spock.lang.Issue

import java.util.concurrent.atomic.AtomicReference

/**
 * Unit tests for the {@link FreezableAttributeContainer} class.
 */
final class FreezableAttributeContainerTest extends BaseAttributeContainerTest {

    def factory = AttributeTestUtil.attributesFactory()

    @Override
    protected FreezableAttributeContainer createContainer(Map<Attribute<?>, ?> attributes = [:], Map<Attribute<?>, ?> moreAttributes = [:]) {
        def mutableContainer = factory.mutable()
        FreezableAttributeContainer container = factory.freezable(mutableContainer, { "owner" } as Describable)
        attributes.forEach { key, value ->
            container.attribute(key, value)
        }
        moreAttributes.forEach { key, value ->
            container.attribute(key, value)
        }
        return container
    }

    def "can add 2 identically named attributes with the same type, resulting in a single entry and no exception thrown"() {
        def container = createContainer()

        when:
        container.attribute(Attribute.of("test", String), "a")
        container.attribute(Attribute.of("test", String), "b")

        then:
        container.asMap().with {
            assert it.size() == 1
            assert it[Attribute.of("test", String)] == "b" // Second attribute to be added remains
        }
    }

    interface Foo extends Named {}

    @Issue("https://github.com/gradle/gradle/issues/37256")
    def "can addAllLater with freezable container as a source, when frozen container uses itself to instantiate named object"() {
        def attr = Attribute.of("test", Foo)

        def freezable = createContainer()
        freezable.attributeProvider(attr, new DefaultProvider<Foo>(() -> {
            freezable.named(Foo, "value")
        }))

        def other = factory.mutable()
        other.addAllLater(freezable)
        freezable.freeze()

        expect:
        other.getAttribute(attr).name == "value"
    }

    def "attributes sourced from addAllLater are frozen"() {
        def attr = Attribute.of("test", String)
        def freezable = createContainer()

        AtomicReference<String> value = new AtomicReference<>("initial")
        freezable.attributeProvider(attr, new DefaultProvider<String>(() -> value.get()))

        def other = factory.mutable()
        other.addAllLater(freezable)

        expect:
        other.getAttribute(attr) == "initial"

        when:
        freezable.freeze()
        value.set("final")

        then:
        other.getAttribute(attr) == "initial"
    }

}
