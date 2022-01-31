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

import spock.lang.Specification

import java.nio.CharBuffer

import static org.gradle.util.internal.GUtil.addToCollection
import static org.gradle.util.internal.GUtil.asPath
import static org.gradle.util.internal.GUtil.collectionize
import static org.gradle.util.internal.GUtil.endsWith
import static org.gradle.util.internal.GUtil.flatten
import static org.gradle.util.internal.GUtil.flattenElements
import static org.gradle.util.internal.GUtil.isSecureUrl
import static org.gradle.util.internal.GUtil.toCamelCase
import static org.gradle.util.internal.GUtil.toConstant
import static org.gradle.util.internal.GUtil.toEnum
import static org.gradle.util.internal.GUtil.toEnumSet
import static org.gradle.util.internal.GUtil.toLowerCamelCase
import static org.gradle.util.internal.GUtil.toWords

class GUtilTest extends Specification {
    static sep = File.pathSeparator

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

    def convertStringToConstantName() {
        expect:
        toConstant(null) == null
        toConstant("") == ""
        toConstant("word") == "WORD"
        toConstant("twoWords") == "TWO_WORDS"
        toConstant("TwoWords") == "TWO_WORDS"
        toConstant("two-words") == "TWO_WORDS"
        toConstant("TWO_WORDS") == "TWO_WORDS"
        toConstant("two.words") == "TWO_WORDS"
        toConstant("two words") == "TWO_WORDS"
        toConstant("two Words") == "TWO_WORDS"
        toConstant("Two Words") == "TWO_WORDS"
        toConstant(" Two  \t words\n") == "TWO_WORDS"
        toConstant("four or so Words") == "FOUR_OR_SO_WORDS"
        toConstant("trailing-") == "TRAILING"
        toConstant("a") == "A"
        toConstant("A") == "A"
        toConstant("aB") == "A_B"
        toConstant("ABC") == "ABC"
        toConstant("ABCThing") == "ABC_THING"
        toConstant("ABC Thing") == "ABC_THING"
        toConstant("123-project") == "123_PROJECT"
        toConstant("123abc-project") == "123ABC_PROJECT"
        toConstant("i18n-admin") == "I18N_ADMIN"
        toConstant(".") == ""
        toConstant("-") == ""
    }

    def convertStringToWords() {
        expect:
        toWords(null) == null
        toWords("") == ""
        toWords("word") == "word"
        toWords("twoWords") == "two words"
        toWords("TwoWords") == "two words"
        toWords("two words") == "two words"
        toWords("Two Words") == "two words"
        toWords(" Two  \t words\n") == "two words"
        toWords("two_words") == "two words"
        toWords("two.words") == "two words"
        toWords("two,words") == "two words"
        toWords("trailing-") == "trailing"
        toWords("a") == "a"
        toWords("aB") == "a b"
        toWords("ABC") == "abc"
        toWords("ABCThing") == "abc thing"
        toWords("ABC Thing") == "abc thing"
        toWords("123Thing") == "123 thing"
        toWords(".") == ""
        toWords("_") == ""
    }

    def "flattens maps and arrays"() {
        expect:
        flatten([[1], [foo: 'bar']]) == [1, 'bar']

        Object[] array = [1]
        flatten([array, [foo: 'bar']]) == [1, 'bar']
    }

    def "flattening of maps controls flatting of arrays"() {
        //documenting current functionality, not sure if the functionality is a good one
        when:
        def out = []
        Object[] array = [1]
        then:
        flatten([array, [foo: 'bar']], out, true) == [1, 'bar']

        when:
        out = []
        array = [1]
        then:
        flatten([array, [foo: 'bar']], out, false) == [[1], [foo: 'bar']]
    }

    def "normalizes to collection"() {
        expect:
        collectionize(null) == []
        collectionize(10) == [10]
        collectionize("a") == ["a"]

        List list = [2, 2, "three"] as List
        collectionize(list) == [2, 2, "three"]

        Object[] array = [1, 1, "three"]
        collectionize(array) == [1, 1, "three"]

        collectionize([list, array]) == [2, 2, "three", 1, 1, "three"]

        collectionize([[1], [hey: 'man']]) == [1, [hey: 'man']]
    }

    def "flattens"() {
        expect:
        flattenElements(1, [2, 3]) == [1, 2, 3]
    }

    def "convert to path notation"() {
        expect:
        asPath(["lib1.jar", "lib2.jar", new File("lib3.jar")]) == "lib1.jar${sep}lib2.jar${sep}lib3.jar"
    }

    def "adds to collection"() {
        def list = [0]
        when:
        addToCollection(addToCollection(list, [1, 2]), [2, 3])
        then:
        list == [0, 1, 2, 2, 3]
    }

    def "adds empty list to collection"() {
        expect:
        addToCollection([], []) == []
        addToCollection([1], []) == [1]
    }

    def "adds to collection preventing nulls"() {
        when:
        addToCollection([], true, [1, 2, null, 3])
        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains([1, 2, null, 3].toString())
    }

    def "can convert strings to enums using the enum value names"() {
        expect:
        TestEnum.ENUM1 == toEnum(TestEnum, "ENUM1")
        TestEnum.ENUM1 == toEnum(TestEnum, "enum1")
        TestEnum.ENUM1 == toEnum(TestEnum, "EnUm1")
        TestEnum.ENUM2 == toEnum(TestEnum, "enum2")
        TestEnum.ENUM3 == toEnum(TestEnum, "enum3")
        TestEnum.ENUM_4_1 == toEnum(TestEnum, "enum_4_1")
        TestEnum.STANDARD_OUT == toEnum(TestEnum, "Standard_Out")
        TestEnum.STANDARD_OUT == toEnum(TestEnum, "standardOut")
    }

    def "converts enum constants to themselves"() {
        expect:
        TestEnum.ENUM1 == toEnum(TestEnum, TestEnum.ENUM1)
    }

    def "fails when trying to convert incompatible types to enums"() {
        when:
        toEnum(TestEnum, 2)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Cannot convert value '2' of type 'java.lang.Integer' to enum type 'org.gradle.util.internal.GUtilTest\$TestEnum'"

    }

    def "can convert multiple strings to an enum set"() {
        expect:
        [TestEnum.ENUM1, TestEnum.ENUM2, TestEnum.ENUM3] as Set == toEnumSet(TestEnum, "Enum1", "EnUm2", TestEnum.ENUM3)
    }

    def "reports available enum values for non convertible strings"() {
        when:
        toEnum(TestEnum, value)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Cannot convert string value '$value' to an enum value of type 'org.gradle.util.internal.GUtilTest\$TestEnum' (valid case insensitive values: ENUM1, ENUM2, ENUM3, ENUM_4_1, STANDARD_OUT)"

        where:
        value      | _
        "notKnown" | _
        "3"        | _
        "3"        | _
    }

    def "can check if one CharSequence ends with another"() {
        expect:
        endsWith(a, b) == a.endsWith(b)
        endsWith(CharBuffer.wrap(a), CharBuffer.wrap(b)) == a.endsWith(b)

        where:
        a     | b
        "foo" | "fo"
        "foo" | "oo"
        "fo"  | "foo"
        "foo" | ""
        ""    | "foo"
    }

    static enum TestEnum {
        ENUM1,
        ENUM2,
        ENUM3(){
            @Override
            String toString() {
                return "3"
            }
        },
        ENUM_4_1,
        STANDARD_OUT
    }

    def "identifies insecure urls"() {
        expect:
        // HTTP is insecure
        !isSecureUrl(new URI("http://example.com"))
        !isSecureUrl(new URI("http://localhost"))
        // Except, we allow 127.0.0.1 to be seen as secure
        isSecureUrl(new URI("http://127.0.0.1"))

        // HTTPS is secure
        isSecureUrl(new URI("https://example.com"))
        isSecureUrl(new URI("https://localhost"))
        isSecureUrl(new URI("https://127.0.0.1"))
    }
}
