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

import org.gradle.api.provider.Provider
import spock.lang.Unroll

abstract class LockableCollectionPropertySpec<C extends Collection<String>> extends LockablePropertySpec<C> {
    abstract CollectionPropertyInternal<String, C> target()

    abstract LockableCollectionProperty<String, C> property(PropertyInternal<C> target)

    CollectionPropertyInternal<String, C> target = target()
    LockableCollectionProperty<String, C> property = property(target)

    C mutate(C value) {
        value.add("more")
        return value
    }

    def "delegates to original property before collection property is locked"() {
        given:
        def provider = Stub(Provider)

        when:
        property.add("123")

        then:
        1 * target.add("123")

        when:
        property.add(provider)

        then:
        1 * target.add(provider)
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

    @Unroll
    def "cannot add elements using #display after property is locked"() {
        given:
        property.lockNow()

        when:
        property.addAll(value)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'This property is locked and cannot be changed.'

        and:
        0 * target._

        where:
        value                       | display
        Stub(Provider)              | "provider"
        ["a", "b", "c"]             | "collection"
        ["a", "b", "c"] as String[] | "array"
    }
}
