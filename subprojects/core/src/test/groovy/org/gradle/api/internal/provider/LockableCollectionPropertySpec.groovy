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
import spock.lang.Specification

abstract class LockableCollectionPropertySpec<C extends Collection<String>> extends Specification {
    abstract CollectionPropertyInternal<String, C> target()
    abstract LockableCollectionProperty<String, C> property(CollectionPropertyInternal<String, C> target)
    abstract C toMutable(Collection<String> values)
    abstract C toImmutable(Collection<String> values)

    CollectionPropertyInternal<String, C> target = target()
    LockableCollectionProperty<String, C> property = property(target)

    def "delegates to original property before property is locked"() {
        when:
        property.add("123")

        then:
        1 * target.add("123")

        when:
        def result = property.get()

        then:
        result == toMutable(["abc"])
        1 * target.getOrNull() >> toMutable(["abc"])

        when:
        def present = property.present

        then:
        !present
        1 * target.getOrNull() >> null

        when:
        def transformer = Stub(Transformer)
        transformer.transform(toMutable(["abc"])) >> "123"
        transformer.transform(toMutable(["bcd"])) >> "456"
        def mapped = property.map(transformer)
        def result1 = mapped.get()
        def result2 = mapped.get()

        then:
        result1 == "123"
        result2 == "456"
        1 * target.getOrNull() >> toMutable(["abc"])
        1 * target.getOrNull() >> toMutable(["bcd"])
    }

    def "cannot add element after property is locked"() {
        given:
        property.lockNow()

        when:
        property.add("broken")

        then:
        def e = thrown(IllegalStateException)
        e.message == 'This property is locked and cannot be changed.'

        and:
        0 * target._
    }

    def "cannot add element provider after property is locked"() {
        given:
        property.lockNow()

        when:
        property.add(Stub(Provider))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'This property is locked and cannot be changed.'

        and:
        0 * target._
    }

    def "cannot add elements provider after property is locked"() {
        given:
        property.lockNow()

        when:
        property.addAll(Stub(Provider))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'This property is locked and cannot be changed.'

        and:
        0 * target._
    }

    def "cannot set elements after property is locked"() {
        given:
        property.lockNow()

        when:
        property.set(["broken"])

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
        def value = toMutable(["123"])

        when:
        property.lockNow()

        then:
        1 * target.getOrNull() >> value
        0 * target._

        when:
        value.add("more")
        def result1 = property.get()
        def result2 = property.getOrNull()
        def result3 = property.getOrElse(toMutable(["broken"]))

        then:
        result1 == toImmutable(["123"])
        result2 == toImmutable(["123"])
        result3 == toImmutable(["123"])

        and:
        0 * target._
    }

    def "changes from original property are not visible after property is locked and property had no value"() {
        when:
        property.lockNow()

        then:
        1 * target.getOrNull() >> null
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
        def value = toMutable(["123"])

        when:
        def transformer = Mock(Transformer)
        def mapped = property.map(transformer)
        property.lockNow()

        then:
        1 * target.getOrNull() >> value
        0 * target._

        when:
        value.add("more")
        def result1 = mapped.get()
        def result2 = mapped.getOrNull()
        def result3 = mapped.getOrElse(200)

        then:
        result1 == 45
        result2 == 46
        result3 == 47

        and:
        1 * transformer.transform(toImmutable(["123"])) >> 45
        1 * transformer.transform(toImmutable(["123"])) >> 46
        1 * transformer.transform(toImmutable(["123"])) >> 47
        0 * target._
    }
}
