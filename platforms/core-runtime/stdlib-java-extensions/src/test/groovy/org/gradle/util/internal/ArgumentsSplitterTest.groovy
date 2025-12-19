/*
 * Copyright 2009 the original author or authors.
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

import spock.lang.Specification

import static org.gradle.util.internal.ArgumentsSplitter.split

class ArgumentsSplitterTest extends Specification {

    def "breaks up empty command line into empty list"() {
        expect:
        split('') == []
    }

    def "breaks up whitespace only command line into empty list"() {
        expect:
        split(' \t ') == []
    }

    def "breaks up command line into space separated argument"() {
        expect:
        split('a') == ['a']
        split('a b\tc') == ['a', 'b', 'c']
    }

    def "ignores extra white space between arguments"() {
        expect:
        split('  a \t') == ['a']
        split('a  \t\t b ') == ['a', 'b']
    }

    def "breaks up command line into double quoted arguments"() {
        expect:
        split('"a b c"') == ['a b c']
        split('a "b c d" e') == ['a', 'b c d', 'e']
        split('a "  b c d  "') == ['a', '  b c d  ']
    }

    def "breaks up command line into single quoted arguments"() {
        expect:
        split("'a b c'") == ['a b c']
        split("a 'b c d' e") == ['a', 'b c d', 'e']
        split("a '  b c d  '") == ['a', '  b c d  ']
    }

    def "can have empty quoted argument"() {
        expect:
        split('""') == ['']
        split("''") == ['']
    }

    def "can have quote inside quoted argument"() {
        expect:
        split('"\'quoted\'"') == ['\'quoted\'']
        split("'\"quoted\"'") == ['"quoted"']
    }

    def "argument can have quoted and unquoted parts"() {
        expect:
        split('a"b "c') == ['ab c']
        split("a'b 'c") == ['ab c']
    }

    def "can have missing end quote"() {
        expect:
        split('"a b c') == ['a b c']
    }
}
