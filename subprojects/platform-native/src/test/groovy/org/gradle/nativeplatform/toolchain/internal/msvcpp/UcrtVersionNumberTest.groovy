/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.msvcpp

import spock.lang.Specification

class UcrtVersionNumberTest extends Specification {
    def "parsing"() {
        expect:
        UcrtVersionNumber.parse('1') == new UcrtVersionNumber(1)
        UcrtVersionNumber.parse('1.2.3') == new UcrtVersionNumber(1, 2, 3)
    }

    def "equality"() {
        given:
        def a = UcrtVersionNumber.parse('1.1')
        def b = UcrtVersionNumber.parse('1.1')
        def c = UcrtVersionNumber.parse('1.2')
        def d = UcrtVersionNumber.parse('1.2.3')

        expect:
        a == b
        a != c
        b != c
        a != d
        b != d
        c != d
    }

    def "comparison"() {
        given:
        def a = UcrtVersionNumber.parse('1.1')
        def b = UcrtVersionNumber.parse('1.1')
        def c = UcrtVersionNumber.parse('1.2')
        def d = UcrtVersionNumber.parse('1.2.3')

        expect:
        a.compareTo(b) == 0
        a.compareTo(c) == -1
        a.compareTo(d) == -1
        c.compareTo(a) == 1
        d.compareTo(a) == 1
        c.compareTo(d) == -1
        d.compareTo(c) == 1
    }

    def "toString" () {
        def a = UcrtVersionNumber.parse('1')
        def b = UcrtVersionNumber.parse('1.2.3')

        expect:
        a.toString() == '1'
        b.toString() == '1.2.3'
    }
}
