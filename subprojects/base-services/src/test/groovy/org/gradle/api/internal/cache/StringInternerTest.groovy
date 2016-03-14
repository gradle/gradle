/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.cache

import spock.lang.Specification
import spock.lang.Subject


class StringInternerTest extends Specification {
    @Subject
    StringInterner stringInterner = new StringInterner()

    def "should return null when null is passed to intern method"() {
        expect:
        stringInterner.intern(null) == null
    }

    def "should return first instance when multiple strings with similar contents are interned"() {
        given:
        def strings = (1..5).collect { new String('hello') }
        assert strings.collect { System.identityHashCode(it) }.unique().size() == 5
        def firstInstance = strings.first()
        when:
        def internedStrings = strings.collect { stringInterner.intern(it) }
        then:
        internedStrings.collect { System.identityHashCode(it) }.unique().size() == 1
        internedStrings.every { it.is(firstInstance) }
    }

    def "should only intern similar strings"() {
        given:
        def createStrings = { (1..5).collect { new String('hello' + it) } }
        def allStrings = createStrings() + createStrings() + createStrings()
        assert allStrings.collect { System.identityHashCode(it) }.unique().size() == 15
        assert allStrings.size() == 15
        when:
        def internedStrings = allStrings.collect { stringInterner.intern(it) }
        then:
        internedStrings.size() == 15
        internedStrings.collect { System.identityHashCode(it) }.unique().size() == 5
    }
}
