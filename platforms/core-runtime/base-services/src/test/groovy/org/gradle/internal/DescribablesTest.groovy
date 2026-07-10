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

package org.gradle.internal

import org.gradle.api.Describable
import spock.lang.Specification

class DescribablesTest extends Specification {
    def "creates from single object"() {
        expect:
        Describables.of("yo").displayName == "yo"
        Describables.of("yo").toString() == "yo"
        Describables.of("yo").capitalizedDisplayName == "Yo"

        Describables.of(123).displayName == "123"
        Describables.of(123).toString() == "123"
        Describables.of(123).capitalizedDisplayName == "123"
    }

    def "creates from parts"() {
        expect:
        Describables.of("one", "two").displayName == "one two"
        Describables.of("one", "two").toString() == "one two"
        Describables.of("one", "two").capitalizedDisplayName == "One two"

        Describables.of(123, 456).displayName == "123 456"
        Describables.of(123, 456).toString() == "123 456"
        Describables.of(123, 456).capitalizedDisplayName == "123 456"

        Describables.of("one", "two", "three").displayName == "one two three"
        Describables.of("one", "two", "three").toString() == "one two three"
        Describables.of("one", "two", "three").capitalizedDisplayName == "One two three"

        Describables.of(123, 456, 789).displayName == "123 456 789"
        Describables.of(123, 456, 789).toString() == "123 456 789"
        Describables.of(123, 456, 789).capitalizedDisplayName == "123 456 789"
    }

    def "creates from describable"() {
        def d1 = Mock(Describable)
        def d2 = Mock(DisplayName)

        given:
        d1.displayName >> "d1"
        d2.displayName >> "d2"
        d2.capitalizedDisplayName >> "D-TWO"

        expect:
        def v1 = Describables.of(d1)
        v1.displayName == "d1"
        v1.toString() == "d1"
        v1.capitalizedDisplayName == "D1"

        def v2 = Describables.of(d2)
        v2.is(d2)

        def v3 = Describables.of(d2, d1)
        v3.displayName == "d2 d1"
        v3.toString() == "d2 d1"
        v3.capitalizedDisplayName == "D-TWO d1"

        def v4 = Describables.of(d2, "and", d1)
        v4.displayName == "d2 and d1"
        v4.toString() == "d2 and d1"
        v4.capitalizedDisplayName == "D-TWO and d1"
    }

    def "creates from dynamic parts"() {
        def left = Mock(Describable)

        given:
        left.displayName >>> ["a", "b", "c", "d", "e", "f"]

        expect:
        def value = Describables.of(left, "two", "three")
        value.displayName == "a two three"
        value.displayName == "b two three"
        value.toString() == "c two three"
        value.toString() == "d two three"
        value.capitalizedDisplayName == "E two three"
        value.capitalizedDisplayName == "F two three"
    }

    def "creates from type and name"() {
        expect:
        def value = Describables.withTypeAndName("some type", "name")
        value.displayName == "some type 'name'"
        value.toString() == "some type 'name'"
        value.capitalizedDisplayName == "Some type 'name'"
    }

    def "creates from description and value"() {
        expect:
        def value = Describables.quoted("some thing", "name")
        value.displayName == "some thing 'name'"
        value.toString() == "some thing 'name'"
        value.capitalizedDisplayName == "Some thing 'name'"
    }

    def "memoizes another describable"() {
        def d1 = Mock(Describable)
        def d2 = Mock(DisplayName)

        when:
        def v1 = Describables.memoize(d1)

        then:
        0 * d1._

        when:
        def name = v1.displayName
        v1.toString()
        v1.capitalizedDisplayName

        then:
        name == "value"
        1 * d1.displayName >> "value"
        0 * d1._

        when:
        def v2 = Describables.memoize(d2)

        then:
        0 * d2._

        when:
        name = v2.displayName
        v2.toString()
        v2.displayName
        def cname = v2.capitalizedDisplayName
        v2.capitalizedDisplayName

        then:
        name == "value"
        cname == "THE VALUE"
        1 * d2.displayName >> "value"
        1 * d2.capitalizedDisplayName >> "THE VALUE"
        0 * d2._

        when:
        def v3 = Describables.memoize(d1)
        def v4 = Describables.memoize(d2)

        v3.capitalizedDisplayName
        v3.capitalizedDisplayName
        v3.displayName
        v3.displayName
        v4.capitalizedDisplayName
        v4.capitalizedDisplayName
        v4.displayName
        v4.displayName

        then:
        1 * d1.displayName >> "d1"
        1 * d2.displayName >> "d2"
        1 * d2.capitalizedDisplayName >> "DEE TWO"
        0 * d2._
        0 * d1._
    }
}
