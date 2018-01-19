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

package org.gradle.api.internal.provider

import org.gradle.api.Transformer
import org.gradle.api.provider.Provider
import org.gradle.testing.internal.util.Specification

abstract class LockablePropertySpec<T> extends Specification {

    abstract T toMutable(T value)

    abstract T toImmutable(T value)

    abstract PropertyInternal<T> target()

    abstract AbstractLockableProperty<T> property(PropertyInternal<T> target)

    abstract T someValue()

    abstract T someOtherValue()

    abstract T brokenValue()

    abstract T mutate(T value)

    abstract PropertyInternal<T> getTarget()
    abstract AbstractLockableProperty<T> getProperty()

    def "delegates to original property before property is locked"() {
        when:
        property.getType()

        then:
        1 * target.getType()

        when:
        property.set(someValue())

        then:
        1 * target.set(someValue())

        when:
        def result = property.get()

        then:
        result == toMutable(someValue())
        1 * target.getOrNull() >> toMutable(someValue())

        when:
        def present = property.present

        then:
        !present
        1 * target.getOrNull() >> null

        when:
        def transformer = Stub(Transformer)
        transformer.transform(toMutable(someValue())) >> 123
        transformer.transform(toMutable(someOtherValue())) >> 456
        def mapped = property.map(transformer)
        def result1 = mapped.get()
        def result2 = mapped.get()

        then:
        result1 == 123
        result2 == 456
        1 * target.getOrNull() >> toMutable(someValue())
        1 * target.getOrNull() >> toMutable(someOtherValue())
    }

    def "cannot set elements after property is locked"() {
        given:
        property.lockNow()

        when:
        property.set(brokenValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'This property is locked and cannot be changed.'

        and:
        0 * target._
    }

    def "cannot set element provider after property is locked"() {
        given:
        property.lockNow()

        when:
        property.set(Stub(Provider))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'This property is locked and cannot be changed.'

        and:
        0 * target._
    }

    def "cannot set from any value after property is locked"() {
        given:
        property.lockNow()

        when:
        property.setFromAnyValue(123)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'This property is locked and cannot be changed.'

        and:
        0 * target._
    }

    def "changes from original property are not visible after property is locked"() {
        def value = toMutable(someValue())

        when:
        property.lockNow()

        then:
        1 * target.getOrNull() >> { value }
        1 * target.getType()
        0 * target._

        when:
        value = mutate(value)
        def result1 = property.get()
        def result2 = property.getOrNull()
        def result3 = property.getOrElse(toMutable(brokenValue()))

        then:
        result1 == toImmutable(someValue())
        result2 == toImmutable(someValue())
        result3 == toImmutable(someValue())

        and:
        0 * target._
    }

    def "changes from original property are not visible after property is locked and property had no value"() {
        when:
        property.lockNow()

        then:
        1 * target.getOrNull() >> null
        1 * target.getType()
        0 * target._

        when:
        def result1 = property.present
        def result2 = property.getOrNull()

        then:
        !result1
        result2 == null

        and:
        0 * target._
    }

    def "changes from original property are not visible through a mapped provider after property is locked"() {
        def value = toMutable(someValue())

        when:
        def transformer = Mock(Transformer)
        def mapped = property.map(transformer)
        property.lockNow()

        then:
        1 * target.getOrNull() >> { value }
        1 * target.getType()
        0 * target._

        when:
        value = mutate(value)
        def result1 = mapped.get()
        def result2 = mapped.getOrNull()
        def result3 = mapped.getOrElse(200)

        then:
        result1 == 45
        result2 == 46
        result3 == 47

        and:
        1 * transformer.transform(toImmutable(someValue())) >> 45
        1 * transformer.transform(toImmutable(someValue())) >> 46
        1 * transformer.transform(toImmutable(someValue())) >> 47
        0 * target._
    }

    def "can query type from original property locked"() {
        given:
        target.getType() >> String
        property.lockNow()

        expect:
        property.getType() == String
    }
}
