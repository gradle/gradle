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
    def "creates from fixed object"() {
        expect:
        Describables.of("yo").displayName == "yo"
        Describables.of("yo").toString() == "yo"
        Describables.of(123).displayName == "123"
        Describables.of(123).toString() == "123"
    }

    def "creates from fixed parts"() {
        expect:
        Describables.of("one", "two").displayName == "one two"
        Describables.of("one", "two").toString() == "one two"
        Describables.of(123, 456).displayName == "123 456"
        Describables.of(123, 456).toString() == "123 456"

        Describables.of("one", "two", "three").displayName == "one two three"
        Describables.of("one", "two", "three").toString() == "one two three"
        Describables.of(123, 456, 789).displayName == "123 456 789"
        Describables.of(123, 456, 789).toString() == "123 456 789"
    }

    def "creates from dynamic parts"() {
        def left = Mock(Describable)

        given:
        left.displayName >>> ["a", "b", "c", "d"]

        expect:
        def value = Describables.of(left, "two", "three")
        value.displayName == "a two three"
        value.displayName == "b two three"
        value.toString() == "c two three"
        value.toString() == "d two three"
    }
}
