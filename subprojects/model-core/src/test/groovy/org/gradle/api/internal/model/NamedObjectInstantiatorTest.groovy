/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.model

import org.gradle.api.Named
import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.state.Managed
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.Matchers

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier

class NamedObjectInstantiatorTest extends ConcurrentSpec {
    static factory = new NamedObjectInstantiator(new TestCrossBuildInMemoryCacheFactory())

    def "creates instance of Named"() {
        expect:
        def n1 = factory.named(Named, "a")
        def n2 = factory.named(Named, "b")

        n1.is(n1)
        !n1.is(n2)

        n1.name == "a"
        n2.name == "b"

        Matchers.strictlyEquals(n1, n1)
        n2 != n1
        !n2.equals(n1)

        n1.hashCode() == n1.hashCode()
        n1.hashCode() != n2.hashCode()

        n1.toString() == "a"
        n2.toString() == "b"

        n1.is(factory.named(Named, "a"))
        n2.is(factory.named(Named, "b"))
    }

    def "can unpack and recreate Named instance"() {
        expect:
        def n1 = factory.named(Named, "a")
        n1 instanceof Managed
        n1.publicType() == Named
        n1.isImmutable()
        def state = n1.unpackState()
        state == "a"

        def n2 = factory.fromState(Named, state)
        n2.is(n1)
    }

    def "creates instance of subtype of Named"() {
        expect:
        def n1 = factory.named(CustomNamed, "a")
        def n2 = factory.named(CustomNamed, "b")

        n1.is(n1)
        !n1.is(n2)

        n1.name == "a"
        n2.name == "b"

        Matchers.strictlyEquals(n1, n1)
        n2 != n1
        !n2.equals(n1)

        n1.hashCode() == n1.hashCode()
        n1.hashCode() != n2.hashCode()

        n1.toString() == "a"
        n2.toString() == "b"

        n1.is(factory.named(CustomNamed, "a"))
        n2.is(factory.named(CustomNamed, "b"))

        !n1.is(factory.named(Named, "a"))
        !n2.is(factory.named(Named, "b"))

        n1 instanceof Managed
    }

    def "can unpack and recreate instance of subtype of Named"() {
        expect:
        def n1 = factory.named(CustomNamed, "a")
        n1 instanceof Managed
        n1.publicType() == CustomNamed
        n1.isImmutable()
        def state = n1.unpackState()
        state == "a"

        def n2 = factory.fromState(CustomNamed, state)
        n2.is(n1)
    }

    def "multiple threads can create different instances of same type"() {
        def results = new CopyOnWriteArrayList()
        def barrier = new CyclicBarrier(10)

        when:
        async {
            10.times { n ->
                start {
                    barrier.await()
                    results.add(factory.named(CustomNamed, n as String))
                }
            }
        }

        then:
        results.size() == 10
        results.unique().size() == 10
    }

    def "multiple threads can create same instance"() {
        def results = new CopyOnWriteArrayList()
        def barrier = new CyclicBarrier(10)

        when:
        async {
            10.times { n ->
                start {
                    barrier.await()
                    results.add(factory.named(CustomNamed, "value"))
                }
            }
        }

        then:
        results.size() == 10
        results.unique().size() == 1
    }

    def "creates instance of abstract Java class"() {
        expect:
        def n1 = factory.named(AbstractNamed, "a")
        def n2 = factory.named(AbstractNamed, "b")

        n1.is(n1)
        !n1.is(n2)

        n1.name == "a"
        n2.name == "b"

        n1.calculatedValue == "[a]"
        n2.calculatedValue == "[b]"

        Matchers.strictlyEquals(n1, n1)
        n2 != n1
        !n2.equals(n1)

        n1.hashCode() == n1.hashCode()
        n1.hashCode() != n2.hashCode()

        n1.toString() == "a"
        n2.toString() == "b"

        n1.is(factory.named(AbstractNamed, "a"))
        n2.is(factory.named(AbstractNamed, "b"))

        !n1.is(factory.named(Named, "a"))
        !n2.is(factory.named(Named, "b"))

        AbstractNamed.counter == 2
    }

    def "can unpack and recreate instance of abstract Java class"() {
        expect:
        def n1 = factory.named(AbstractNamed, "a")
        n1 instanceof Managed
        n1.publicType() == AbstractNamed
        n1.isImmutable()
        def state = n1.unpackState()
        state == "a"

        def n2 = factory.fromState(AbstractNamed, state)
        n2.is(n1)
    }

    def "creates instance of Java class with dummy getName() implementation"() {
        expect:
        def n1 = factory.named(DummyNamed, "a")
        def n2 = factory.named(DummyNamed, "b")

        n1.is(n1)
        !n1.is(n2)

        n1.name == "a"
        n2.name == "b"

        n1.calculatedValue == "[a]"
        n2.calculatedValue == "[b]"

        Matchers.strictlyEquals(n1, n1)
        n2 != n1
        !n2.equals(n1)

        n1.hashCode() == n1.hashCode()
        n1.hashCode() != n2.hashCode()

        n1.toString() == "a"
        n2.toString() == "b"

        n1.is(factory.named(DummyNamed, "a"))
        n2.is(factory.named(DummyNamed, "b"))

        !n1.is(factory.named(Named, "a"))
        !n2.is(factory.named(Named, "b"))

        DummyNamed.counter == 2
    }

    def "creates instance of Groovy class with dummy getName() implementation"() {
        expect:
        def n1 = factory.named(DummyGroovyNamed, "a")
        def n2 = factory.named(DummyGroovyNamed, "b")

        n1.is(n1)
        !n1.is(n2)

        n1.name == "a"
        n2.name == "b"

        n1.calculatedValue == "[a]"
        n2.calculatedValue == "[b]"

        Matchers.strictlyEquals(n1, n1)
        n2 != n1
        !n2.equals(n1)

        n1.hashCode() == n1.hashCode()
        n1.hashCode() != n2.hashCode()

        n1.toString() == "a"
        n2.toString() == "b"

        n1.is(factory.named(DummyGroovyNamed, "a"))
        n2.is(factory.named(DummyGroovyNamed, "b"))

        !n1.is(factory.named(Named, "a"))
        !n2.is(factory.named(Named, "b"))
    }

    def "class should not have any instance fields"() {
        when:
        factory.named(NamedWithFields, "a")

        then:
        def e = thrown(ObjectInstantiationException)
        e.message == "Could not create an instance of type ${NamedWithFields.name}."
        e.cause.message == """Type ${NamedWithFields.name} is not a valid Named implementation class:
- Field name is not valid: A Named implementation class must not define any instance fields.
- Field other is not valid: A Named implementation class must not define any instance fields."""

        when:
        factory.named(NamedWithFields.Sub, "a")

        then:
        e = thrown(ObjectInstantiationException)
        e.message == "Could not create an instance of type ${NamedWithFields.Sub.name}."
        e.cause.message == """Type ${NamedWithFields.name}.Sub is not a valid Named implementation class:
- Field NamedWithFields.name is not valid: A Named implementation class must not define any instance fields.
- Field NamedWithFields.other is not valid: A Named implementation class must not define any instance fields.
- Field b is not valid: A Named implementation class must not define any instance fields."""
    }

    def "wraps constructor failure"() {
        when:
        factory.named(BrokenConstructor, "a")

        then:
        def e = thrown(ObjectInstantiationException)
        e.message == "Could not create an instance of type ${BrokenConstructor.name}."
        e.cause == BrokenConstructor.failure
    }

    // TODO
    // Implement:
    // - interface may not have additional methods
    // - abstract class may not have additional abstract methods

}

class DummyGroovyNamed implements Named {
    String getName() { null }

    String getCalculatedValue() { "[$name]" }
}

abstract class BrokenConstructor implements Named {
    static RuntimeException failure = new RuntimeException("broken")

    BrokenConstructor() {
        throw failure
    }
}
