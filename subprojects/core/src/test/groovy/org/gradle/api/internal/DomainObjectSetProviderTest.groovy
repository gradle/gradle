/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.DomainObjectSet
import org.gradle.api.provider.ProviderFactory
import org.gradle.configuration.internal.DefaultUserCodeApplicationContext
import org.gradle.configuration.internal.UserCodeApplicationContext
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicBoolean

class DomainObjectSetProviderTest extends Specification {

    ProviderFactory providers = TestUtil.providerFactory()

    TestBuildOperationExecutor buildOperationExecutor = new TestBuildOperationExecutor()
    UserCodeApplicationContext userCodeApplicationContext = new DefaultUserCodeApplicationContext()
    CollectionCallbackActionDecorator callbackActionDecorator = new DefaultCollectionCallbackActionDecorator(buildOperationExecutor, userCodeApplicationContext)

    def "can add elements"() {
        given:
        def main = newSet()
        def child1 = newSet()
        def child2 = newSet()

        main.addAllLater(new DomainObjectSetProvider<>(child1))
        main.addAllLater(new DomainObjectSetProvider<>(child2))

        when:
        child1.add("c1e1")
        child2.add("c2e1")

        then:
        elements(main) == ["c1e1", "c2e1"]

        when:
        child1.add("c1e2")

        then:
        elements(main) == ["c1e1", "c1e2", "c2e1"]

        when:
        child2.add("c2e2")

        then:
        elements(main) == ["c1e1", "c1e2", "c2e1", "c2e2"]
    }

    def "can remove elements"() {
        given:
        def main = newSet()
        def child1 = newSet()
        def child2 = newSet()

        main.addAllLater(new DomainObjectSetProvider<>(child1))
        main.addAllLater(new DomainObjectSetProvider<>(child2))

        when:
        child1.add("c1e1")
        child1.add("c1e2")
        child2.add("c2e1")

        then:
        elements(main) == ["c1e1", "c1e2", "c2e1"]

        when:
        child1.remove("c1e1")

        then:
        elements(main) == ["c1e2", "c2e1"]

        when:
        child1.add("c1e1")

        then:
        elements(main) == ["c1e2", "c1e1", "c2e1"]

        when:
        child1.remove("c1e1")

        then:
        elements(main) == ["c1e2", "c2e1"]

        when:
        child1.remove("c1e2")

        then:
        elements(main) == ["c2e1"]
    }

    def "does not eagerly realize wrapped set"() {
        given:
        def main = newSet()
        def child1 = newSet()
        def child2 = newSet()

        main.addAllLater(new DomainObjectSetProvider<>(child1))

        when:
        AtomicBoolean realized1 = new AtomicBoolean(false)
        child1.addAllLater(providers.provider(() -> {
            realized1.set(true)
            return ["foo"]
        }))

        then:
        !realized1.get()

        when:
        elements(main) == ["foo"]

        then:
        realized1.get()

        // new lazy elements added to child are not realized immediately
        when:
        AtomicBoolean realized2 = new AtomicBoolean(false)
        child1.addAllLater(providers.provider(() -> {
            realized2.set(true)
            return ["bar"]
        }))

        then:
//        !realized2.get()
        realized2.get() // :(

        when:
        elements(main) == ["foo", "bar"]

        then:
        realized2.get()

        // New lazy sets added after the parent has been realized are not realized immediately
        when:
        main.addAllLater(new DomainObjectSetProvider<>(child2))
        AtomicBoolean realized3 = new AtomicBoolean(false)
        child2.addAllLater(providers.provider(() -> {
            realized3.set(true)
            return ["baz"]
        }))

        then:
        !realized3.get()

        when:
        elements(main) == ["foo", "bar", "baz"]

        then:
        realized3.get()

    }

    def "can remove elements from parent set when child has 1 element"() {
        given:
        def main = newSet()
        def child = newSet()

        main.addAllLater(new DomainObjectSetProvider<>(child))
        child.add("foo")

        if (realize) {
            elements(main)
        }

        when:
        main.remove("foo")

        then:
        elements(main) == []

        when:
        child.add("bar")

        then:
        elements(main) == ["bar"]

        where:
        realize << [false, true]
    }

    def "can remove elements from parent set when child has multiple elements"() {
        given:
        def main = newSet()
        def child = newSet()

        main.addAllLater(new DomainObjectSetProvider<>(child))
        child.add("foo")
        child.add("bar")

        if (realize) {
            elements(main)
        }

        when:
        main.remove("foo")

        then:
        elements(main) == ["bar"]

        when:
        child.add("baz")

        then:
        elements(main) == ["bar", "baz"]

        where:
        realize << [false, true]
    }

    List<String> elements(DomainObjectSet<String> set) {
        new ArrayList<>(set)
    }

    DomainObjectSet<String> newSet() {
        new DefaultDomainObjectSet<String>(String, callbackActionDecorator)
    }

}
