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

    def "returns a factory for a given type"() {
        def fooFactory = factory(Foo)
        def registry = new DefaultManagedFactoryRegistry(fooFactory)

        expect:
        registry.lookup(Foo) == fooFactory
    }

    def "returns a factory for a subtype"() {
        def fooFactory = factory(Foo)
        def registry = new DefaultManagedFactoryRegistry(fooFactory)

        expect:
        registry.lookup(Bar) == fooFactory
    }

    def "returns a factory for an implementing type"() {
        def fooFactory = factory(Foo)
        def registry = new DefaultManagedFactoryRegistry(fooFactory)

        expect:
        registry.lookup(Baz) == fooFactory
    }

    def "selects correct factory among multiple factories"() {
        def fooFactory = factory(Foo)
        def buzzFactory = factory(Buzz)
        def registry = new DefaultManagedFactoryRegistry(buzzFactory, fooFactory)

        expect:
        registry.lookup(Baz) == fooFactory
    }

    def "selects first matching factory"() {
        def fooFactory = factory(Foo)
        def barFactory = factory(Bar)

        when:
        def registry = new DefaultManagedFactoryRegistry(barFactory, fooFactory)

        then:
        registry.lookup(Baz) == barFactory

        when:
        registry = new DefaultManagedFactoryRegistry(fooFactory, barFactory)

        then:
        registry.lookup(Baz) == fooFactory
    }

    def "returns a registered factory for a managed type"() {
        def buzzFactory = factory(Buzz)

        when:
        def registry = new DefaultManagedFactoryRegistry(buzzFactory)

        then:
        registry.lookup(Bar) == null

        when:
        def barFactory = factory(Bar)
        registry.register(Bar, barFactory)

        then:
        registry.lookup(Bar) == barFactory
    }

    def "prefers a registered factory over a known factory for a managed type"() {
        def fooFactory = factory(Foo)

        when:
        def registry = new DefaultManagedFactoryRegistry(fooFactory)

        then:
        registry.lookup(Bar) == fooFactory

        when:
        def barFactory = factory(Bar)
        registry.register(Bar, barFactory)

        then:
        registry.lookup(Bar) == barFactory
    }

    def "returns null for unmanaged type"() {
        def fooFactory = factory(Foo)
        def barFactory = factory(Bar)
        def registry = new DefaultManagedFactoryRegistry(barFactory, fooFactory)

        expect:
        registry.lookup(Fuzz) == null
    }

    ManagedFactory factory(Class<?> type) {
        return Stub(ManagedFactory) {
            _ * canCreate(_) >> { args -> type.isAssignableFrom(args[0]) }
        }
    }

    static interface Foo { }

    static interface Bar extends Foo { }

    static class Baz implements Bar { }

    static interface Buzz { }

    static interface Fuzz { }
}
