/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.state

import spock.lang.Specification

class DefaultManagedFactoryRegistryTest extends Specification {
    def registry = new DefaultManagedFactoryRegistry()

    def "returns a factory for a given type"() {
        def fooFactory = factory(Foo)
        def buzzFactory = factory(Buzz)
        registry.withFactories(buzzFactory, fooFactory)

        expect:
        registry.lookup(fooFactory.id) == fooFactory
    }

    def "returns null for unknown type"() {
        def fooFactory = factory(Foo)
        def barFactory = factory(Bar)
        def buzzFactory = factory(Buzz)
        registry.withFactories(barFactory, fooFactory)

        expect:
        registry.lookup(buzzFactory.id) == null
    }

    def "can lookup factory in parent registry"() {
        def fooFactory = factory(Foo)
        def barFactory = factory(Bar)
        def parent = new DefaultManagedFactoryRegistry().withFactories(fooFactory)
        def registry = new DefaultManagedFactoryRegistry(parent).withFactories(barFactory)

        expect:
        registry.lookup(fooFactory.id) == fooFactory
    }

    def "prefers factory in child registry"() {
        def fooFactory1 = factory(Foo)
        def fooFactory2 = factory(Foo)

        def parent = new DefaultManagedFactoryRegistry().withFactories(fooFactory1)
        def registry = new DefaultManagedFactoryRegistry(parent).withFactories(fooFactory2)

        expect:
        registry.lookup(fooFactory1.id) == fooFactory2
    }

    def "returns null for unknown type in all registries"() {
        def fooFactory = factory(Foo)
        def barFactory = factory(Bar)
        def buzzFactory = factory(Buzz)
        def parent = new DefaultManagedFactoryRegistry().withFactories(fooFactory)
        def registry = new DefaultManagedFactoryRegistry(parent).withFactories(barFactory)

        expect:
        registry.lookup(buzzFactory.id) == null
    }

    def "throws exception when a factory with the same id is registered twice"() {
        def fooFactory1 = factory(Foo)
        def fooFactory2 = factory(Foo)

        when:
        registry.withFactories(fooFactory1, fooFactory2)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.matches("A managed factory with type [^\\s]+ \\(id: [^\\s]+\\) has already been registered.")
    }

    ManagedFactory factory(Class<?> type) {
        return Stub(ManagedFactory) {
            _ * getId() >> Objects.hashCode(type.name)
        }
    }

    static interface Foo { }

    static interface Bar extends Foo { }

    static class Baz implements Bar { }

    static interface Buzz { }
}
