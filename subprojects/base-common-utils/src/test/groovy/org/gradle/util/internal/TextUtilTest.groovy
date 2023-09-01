/*
 * Copyright 2023 the original author or authors.
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

class TextUtilTest extends Specification {
    private static String sep = "separator"
    private static String platformSep = TextUtil.platformLineSeparator

    def convertLineSeparators() {
        expect:
        TextUtil.convertLineSeparators(original, sep) == converted

        where:
        original                          | converted
        ""                                | ""
        "none"                            | "none"
        "one\rtwo\nthree\r\nfour\n\rfive" | "one${sep}two${sep}three${sep}four${sep}${sep}five"
    }

    def toPlatformLineSeparators() {
        expect:
        TextUtil.toPlatformLineSeparators(original) == converted

        where:
        original                          | converted
        ""                                | ""
        "none"                            | "none"
        "one\rtwo\nthree\r\nfour\n\rfive" | "one${platformSep}two${platformSep}three${platformSep}four${platformSep}${platformSep}five"
        "\n\n"                            | "${platformSep}${platformSep}"
    }

    def normaliseLineSeparators() {
        expect:
        TextUtil.normaliseLineSeparators(original) == converted
        TextUtil.normaliseLineSeparators(converted).is converted

        where:
        original                          | converted
        null                              | null
        ""                                | ""
        "none"                            | "none"
        "one\rtwo\nthree\r\nfour\n\rfive" | "one\ntwo\nthree\nfour\n\nfive"
        "\r\n\n\r"                        | "\n\n\n"
    }

    def "convertLineSeparatorsToUnix returns same string when already converted"() {
        expect:
        TextUtil.convertLineSeparatorsToUnix(original).is original

        where:
        original << ["", "none", "one\ntwo\nthree"]
    }

    def containsWhitespace() {
        expect:
        TextUtil.containsWhitespace(str) == whitespace

        where:
        str       | whitespace
        "abcde"   | false
        "abc de"  | true
        " abcde"  | true
        "abcde "  | true
        "abc\tde" | true
        "abc\nde" | true
    }

    def indent() {
        expect:
        TextUtil.indent(text, indent) == result

        where:
        text                | indent | result
        ""                  | ""     | ""
        "abc"               | "  "   | "  abc"
        "abc"               | "def"  | "defabc"
        "abc\ndef\nghi"     | " "    | " abc\n def\n ghi"
        "abc\n\t\n   \nghi" | "X"    | "Xabc\n\t\n   \nXghi"
    }

    def shorterOf() {
        expect:
        TextUtil.shorterOf("a", "b") == "a"
        TextUtil.shorterOf("aa", "b") == "b"
        TextUtil.shorterOf("a", "bb") == "a"
        TextUtil.shorterOf("", "bb") == ""
        TextUtil.shorterOf("", "") == ""
    }

    def "#camelCase to kebab = #kebabCase"() {
        expect:
        TextUtil.camelToKebabCase(camelCase) == kebabCase

        where:
        camelCase   | kebabCase
        ""          | ""
        "foo"       | "foo"
        "fooBar"    | "foo-bar"
        "Foo"       | "foo"
        "fooBarBaz" | "foo-bar-baz"
        "ABC"       | "a-b-c"
        "someT"     | "some-t"
        "sT"        | "s-t"
        "aBc"       | "a-bc"
        "aBec"      | "a-bec"
    }
}
