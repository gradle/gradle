/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.util.Matchers.strictlyEqual

class VersionParserTest extends Specification {

    @Subject
    VersionParser versionParser = new VersionParser()

    def "parsed version is equal when source string is equal"() {
        def v = parse("1.2.b")
        def equal = parse("1.2.b")

        expect:
        v strictlyEqual(equal)
        v != parse("1.2.c")
        v != parse("1.2")
        v != parse("1.2.b.0")
        v != parse("1.2-b")
        v != parse("1.2b")
    }

    def "splits version on punctuation"() {
        expect:
        def version = parse(versionStr)
        version.parts as List == parts

        where:
        versionStr      | parts
        'a.b.c'         | ['a', 'b', 'c']
        'a-b-c'         | ['a', 'b', 'c']
        'a_b_c'         | ['a', 'b', 'c']
        'a+b+c'         | ['a', 'b', 'c']
        'a.b-c+d_e'     | ['a', 'b', 'c', 'd', 'e']
        '\u03b1-\u03b2' | ['\u03b1', '\u03b2']
    }

    def "splits on adjacent digits and alpha"() {
        expect:
        def version = parse(versionStr)
        version.parts as List == parts

        where:
        versionStr       | parts
        'abc123'         | ['abc', '123']
        '1a2b'           | ['1', 'a', '2', 'b']
        '1\u03b12\u03b2' | ['1', '\u03b1', '2', '\u03b2']
    }

    def "base version includes the first . separated parts"() {
        expect:
        def version = parse(versionStr)
        version.baseVersion == parse(baseStr)
        version.qualified == qualified

        where:
        versionStr        | baseStr     | qualified
        "1.2.3"           | "1.2.3"     | false
        "1.2-3"           | "1.2"       | true
        "1.2-beta_3+0000" | "1.2"       | true
        "1.2b3"           | "1.2"       | true
        "1-alpha"         | "1"         | true
        "abc.1-3"         | "abc.1"     | true
        "123"             | "123"       | false
        "abc"             | "abc"       | false
        "a.b.c.1.2"       | "a.b.c.1.2" | false
        "1b2.1.2.3"       | "1"         | true
        "b1-2-3.3"        | "b"         | true
    }

    def "handles empty parts and retains whitespace"() {
        expect:
        def version = parse(versionStr)
        version.parts as List == parts

        where:
        versionStr  | parts
        ''          | []
        'a b c'     | ['a b c']
        '...'       | ['', '', '']
        '-a b c-  ' | ['', 'a b c', '  ']
    }

    def "numeric parts are parsed"() {
        expect:
        def version = parse(versionStr)
        version.numericParts == numericParts.collect { it == null ? null : it.toLong() }.toArray()

        where:
        versionStr        | numericParts
        "1.2.3"           | [1, 2, 3]
        "1.2-3"           | [1, 2, 3]
        "1.2-beta_3+0000" | [1, 2, null, 3, 0]
        "1.2b3"           | [1, 2, null, 3]
        "1-alpha"         | [1, null]
        "abc.1-3"         | [null, 1, 3]
        "123"             | [123]
        "abc"             | [null]
        "a.b.c.1.2"       | [null, null, null, 1, 2]
        "1b2.1.2.3"       | [1, null, 2, 1, 2, 3]
        "b1-2-3.3"        | [null, 1, 2, 3 ,3]
    }

    def parse(String v) {
        return versionParser.transform(v)
    }
}
