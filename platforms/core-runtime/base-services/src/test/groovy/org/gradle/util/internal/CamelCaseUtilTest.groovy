/*
 * Copyright 2025 the original author or authors.
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

import static org.gradle.util.internal.CamelCaseUtil.toCamelCase
import static org.gradle.util.internal.CamelCaseUtil.toLowerCamelCase

/**
 * Unit tests for {@link CamelCaseUtil}.
 */
class CamelCaseUtilTest extends Specification {
    def "#camelCase to kebab = #kebabCase"() {
        expect:
        CamelCaseUtil.camelToKebabCase(camelCase) == kebabCase

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

    def convertStringToCamelCase() {
        expect:
        toCamelCase(null) == null
        toCamelCase("") == ""
        toCamelCase("word") == "Word"
        toCamelCase("twoWords") == "TwoWords"
        toCamelCase("TwoWords") == "TwoWords"
        toCamelCase("two-words") == "TwoWords"
        toCamelCase("two.words") == "TwoWords"
        toCamelCase("two words") == "TwoWords"
        toCamelCase("two Words") == "TwoWords"
        toCamelCase("Two Words") == "TwoWords"
        toCamelCase(" Two  \t words\n") == "TwoWords"
        toCamelCase("four or so Words") == "FourOrSoWords"
        toCamelCase("123-project") == "123Project"
        toCamelCase("i18n-admin") == "I18nAdmin"
        toCamelCase("trailing-") == "Trailing"
        toCamelCase("ABC") == "ABC"
        toCamelCase(".") == ""
        toCamelCase("-") == ""
    }

    def convertStringToLowerCamelCase() {
        expect:
        toLowerCamelCase(null) == null
        toLowerCamelCase("") == ""
        toLowerCamelCase("word") == "word"
        toLowerCamelCase("twoWords") == "twoWords"
        toLowerCamelCase("TwoWords") == "twoWords"
        toLowerCamelCase("two-words") == "twoWords"
        toLowerCamelCase("two.words") == "twoWords"
        toLowerCamelCase("two words") == "twoWords"
        toLowerCamelCase("two Words") == "twoWords"
        toLowerCamelCase("Two Words") == "twoWords"
        toLowerCamelCase(" Two  \t words\n") == "twoWords"
        toLowerCamelCase("four or so Words") == "fourOrSoWords"
        toLowerCamelCase("123-project") == "123Project"
        toLowerCamelCase("i18n-admin") == "i18nAdmin"
        toLowerCamelCase("trailing-") == "trailing"
        toLowerCamelCase("ABC") == "aBC"
        toLowerCamelCase(".") == ""
        toLowerCamelCase("-") == ""
    }
}
