/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.util

import org.gradle.api.Action
import spock.lang.Specification

import java.util.concurrent.Callable

class SwapperTest extends Specification {

    def originalValue = 1
    def newValue = 2

    def value = originalValue
    Callable getter = { value }
    Action setter = { this.value = it }

    void swap(newValue, Runnable whileSwapped) {
        new Swapper(getter, setter)
            .swap(newValue, whileSwapped)
    }

    def "can swap values"() {
        expect:
        swap(newValue) { assert value == newValue }
        value == originalValue
    }

    def "value is swapped back when exception thrown"() {
        when:
        swap(newValue) { throw new IllegalStateException() }

        then:
        value == originalValue
        thrown IllegalStateException
    }
}
