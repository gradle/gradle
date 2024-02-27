/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.util.internal

import org.gradle.api.Action
import org.gradle.internal.Factory
import spock.lang.Specification

class SwapperTest extends Specification {

    def originalValue = 1
    def value = originalValue
    def getter = { value }
    def setter = { this.value = it }

    def swap(newValue, whileSwapped) {
        new Swapper(getter as Factory, setter as Action).swap(newValue, whileSwapped as Factory)
    }

    def "can swap values"() {
        given:
        def newValue = 2
        def returnValue = 3

        expect:
        returnValue == swap(newValue) {
            assert value == newValue
            returnValue
        }

        and:
        value == originalValue
    }

    def "value is swapped back when exception thrown"() {
        when:
        swap(2) {
            throw new IllegalStateException()
        }

        then:
        value == originalValue

        and:
        thrown IllegalStateException
    }

}
