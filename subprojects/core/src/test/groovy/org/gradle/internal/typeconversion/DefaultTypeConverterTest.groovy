/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.typeconversion

import org.gradle.api.internal.file.FileResolver
import org.gradle.util.internal.TextUtil
import spock.lang.Specification

class DefaultTypeConverterTest extends Specification {
    public static final BigInteger LARGE = BigInteger.ONE.shiftLeft(64)
    def converter = new DefaultTypeConverter(Stub(FileResolver))

    def "converts null to null"() {
        expect:
        converter.convert(null, type, false) == null

        where:
        type       | _
        Boolean    | _
        Byte       | _
        Short      | _
        Integer    | _
        Long       | _
        Float      | _
        Double     | _
        BigDecimal | _
        BigInteger | _
        String     | _
        File       | _
    }

    def "widens numeric values to Long"() {
        expect:
        converter.convert(12 as Byte, Long.class, false) == 12L
        converter.convert(123 as Short, Long.class, false) == 123L
        converter.convert(123 as Integer, Long.class, false) == 123L
        converter.convert(123 as Long, Long.class, false) == 123L
        converter.convert(123 as Double, Long.class, false) == 123L
        converter.convert(123 as BigInteger, Long.class, false) == 123L
        converter.convert(123 as BigDecimal, Long.class, false) == 123L
    }

    def "widens numeric values to primitive Long"() {
        expect:
        converter.convert(12 as Byte, Long.class, true) == 12L
        converter.convert(123 as Short, Long.class, true) == 123L
        converter.convert(123 as Integer, Long.class, true) == 123L
        converter.convert(123 as Long, Long.class, true) == 123L
        converter.convert(123 as Double, Long.class, true) == 123L
        converter.convert(123 as BigInteger, Long.class, true) == 123L
        converter.convert(123 as BigDecimal, Long.class, true) == 123L
    }

    def "converts CharSequence to Long"() {
        expect:
        converter.convert("123", Long.class, false) == 123L
        converter.convert(new StringBuilder("123"), Long.class, false) == 123L
    }

    def "converts CharSequence to primitive long"() {
        expect:
        converter.convert("123", Long.class, true) == 123L
        converter.convert(new StringBuilder("123"), Long.class, true) == 123L
    }

    def "fails when value is truncated when converting to Long"() {
        when:
        converter.convert(value, Long.class, false)

        then:
        def e = thrown(TypeConversionException)
        e.message == "Cannot convert value '${value}' to type Long"
        e.cause instanceof ArithmeticException

        where:
        value               | _
        LARGE               | _
        LARGE as BigDecimal | _
        LARGE as String     | _
        12.123              | _
        "0.123"             | _
    }

    def "cannot convert arbitrary type to Long"() {
        when:
        def cl = {}
        converter.convert(cl, Long.class, false)

        then:
        def e = thrown(UnsupportedNotationException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot convert the provided notation to an object of type Long: ${cl.toString()}.
The following types/formats are supported:
  - A String or CharSequence
  - Any Number""")
    }

    def "cannot convert arbitrary string to Long"() {
        when:
        converter.convert("42-not-a-long", Long.class, false)

        then:
        def e = thrown(TypeConversionException)
        e.message == TextUtil.toPlatformLineSeparators("Cannot convert value '42-not-a-long' to type Long")
        e.cause instanceof NumberFormatException
    }

    def "cannot convert arbitrary type to primitive long"() {
        when:
        def cl = {}
        converter.convert(cl, Long.class, true)

        then:
        def e = thrown(UnsupportedNotationException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot convert the provided notation to an object of type long: ${cl.toString()}.
The following types/formats are supported:
  - A String or CharSequence
  - Any Number""")
    }

    def "cannot convert null to primitive long"() {
        when:
        converter.convert(null, Long.class, true)

        then:
        def e = thrown(UnsupportedNotationException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot convert a null value to an object of type long.
The following types/formats are supported:
  - A String or CharSequence
  - Any Number""")
    }

    def "cannot convert arbitrary string to primitive long"() {
        when:
        converter.convert("42-not-a-long", Long.class, true)

        then:
        def e = thrown(TypeConversionException)
        e.message == TextUtil.toPlatformLineSeparators("Cannot convert value '42-not-a-long' to type long")
        e.cause instanceof NumberFormatException
    }

    def "fails when value is truncated when converting to Byte"() {
        when:
        converter.convert(value, Byte.class, false)

        then:
        def e = thrown(TypeConversionException)
        e.message == "Cannot convert value '${value}' to type Byte"
        e.cause instanceof ArithmeticException

        where:
        value             | _
        128l              | _
        128 as BigDecimal | _
        "12045"           | _
        "128"             | _
        "-129"            | _
        12.123            | _
        "0.123"           | _
    }

    def "converts CharSequence to BigDecimal"() {
        expect:
        converter.convert("123", BigDecimal.class, false) == 123
        converter.convert("123.12", BigDecimal.class, false) == 123.12
        converter.convert(new StringBuilder("0.00001"), BigDecimal.class, false) == 0.00001
    }

    def "fails when value is truncated when converting to BigInteger"() {
        when:
        converter.convert(value, BigInteger.class, false)

        then:
        def e = thrown(TypeConversionException)
        e.message == "Cannot convert value '${value}' to type BigInteger"
        e.cause instanceof ArithmeticException

        where:
        value   | _
        12.123  | _
        "0.123" | _
    }

    def "converts numeric values to Double"() {
        expect:
        converter.convert(12 as Byte, Double.class, false) == 12L
        converter.convert(123 as Short, Double.class, false) == 123L
        converter.convert(123 as Integer, Double.class, false) == 123L
        converter.convert(123 as Long, Double.class, false) == 123L
        converter.convert(123 as Float, Double.class, false) == 123L
        converter.convert(123 as Double, Double.class, false) == 123L
        converter.convert(123 as BigInteger, Double.class, false) == 123L
        converter.convert(123 as BigDecimal, Double.class, false) == 123L
    }

    def "converts numeric values to primitive double"() {
        expect:
        converter.convert(12 as Byte, Double.class, true) == 12L
        converter.convert(123 as Short, Double.class, true) == 123L
        converter.convert(123 as Integer, Double.class, true) == 123L
        converter.convert(123 as Long, Double.class, true) == 123L
        converter.convert(123 as Float, Double.class, true) == 123L
        converter.convert(123 as Double, Double.class, true) == 123L
        converter.convert(123 as BigInteger, Double.class, true) == 123L
        converter.convert(123 as BigDecimal, Double.class, true) == 123L
    }

    def "converts CharSequence to Boolean"() {
        expect:
        converter.convert("123", Boolean.class, false) == false
        converter.convert(new StringBuilder("true"), Boolean.class, false) == true
    }

    def "converts CharSequence to primitive boolean"() {
        expect:
        converter.convert("123", Boolean.class, true) == false
        converter.convert(new StringBuilder("true"), Boolean.class, true) == true
    }

    def "converts Boolean to primitive boolean"() {
        expect:
        converter.convert(Boolean.TRUE, Boolean.class, true) == true
        converter.convert(Boolean.FALSE, Boolean.class, true) == false
    }

    def "converts Boolean to Boolean"() {
        expect:
        converter.convert(Boolean.TRUE, Boolean.class, false) == true
        converter.convert(Boolean.FALSE, Boolean.class, false) == false
    }

    def "cannot convert arbitrary type to Boolean"() {
        when:
        def cl = {}
        converter.convert(cl, Boolean.class, false)

        then:
        def e = thrown(UnsupportedNotationException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot convert the provided notation to an object of type Boolean: ${cl.toString()}.
The following types/formats are supported:
  - A String or CharSequence
  - A Boolean""")
    }

    def "converts scalar types to Character"() {
        expect:
        converter.convert("a" as Character, Character.class, false) == "a"
        converter.convert("a", Character.class, false) == "a"
        converter.convert(new StringBuilder("a"), Character.class, false) == "a"
    }

    def "cannot convert string that is not exactly one character long to character"() {
        when:
        converter.convert(value, Character.class, false)

        then:
        def e = thrown(TypeConversionException)
        e.message == "Cannot convert string value '${value}' with length ${value.length()} to type Character"

        where:
        value << ["", "123"]
    }

    def "cannot convert string that is not exactly one character long to primitive char"() {
        when:
        converter.convert(value, Character.class, true)

        then:
        def e = thrown(TypeConversionException)
        e.message == "Cannot convert string value '${value}' with length ${value.length()} to type char"

        where:
        value << ["", "123"]
    }

    def "cannot convert arbitrary type to Character"() {
        when:
        def cl = {}
        converter.convert(cl, Character.class, false)

        then:
        def e = thrown(UnsupportedNotationException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot convert the provided notation to an object of type Character: ${cl.toString()}.
The following types/formats are supported:
  - A String or CharSequence
  - A Character""")
    }

    def "cannot convert arbitrary type to primitive char"() {
        when:
        def cl = {}
        converter.convert(cl, Character.class, true)

        then:
        def e = thrown(UnsupportedNotationException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot convert the provided notation to an object of type char: ${cl.toString()}.
The following types/formats are supported:
  - A String or CharSequence
  - A Character""")
    }

    def "converts CharSequence to String"() {
        expect:
        converter.convert("123", String.class, false) == "123"
        converter.convert(new StringBuilder("123"), String.class, false) == "123"
    }

    def "converts scalar types to String"() {
        expect:
        converter.convert('a' as Character, String.class, false) == "a"
        converter.convert(12 as Byte, String.class, false) == "12"
        converter.convert(123 as Long, String.class, false) == "123"
        converter.convert(123 as BigInteger, String.class, false) == "123"
        converter.convert(true, String.class, false) == "true"
        converter.convert(new File("f"), String.class, false) == "f"
    }

    def "cannot convert arbitrary type to String"() {
        when:
        def cl = {}
        converter.convert(cl, String.class, false)

        then:
        def e = thrown(UnsupportedNotationException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot convert the provided notation to a String: ${cl.toString()}.
The following types/formats are supported:
  - A String or CharSequence or Character
  - Any Number
  - A Boolean
  - A File""")
    }

    enum Thing {
        SOME_THING, SOME_OTHER_THING
    }

    def "converts CharSequence to Enum"() {
        expect:
        converter.convert("SOME_THING", Thing.class, false) == Thing.SOME_THING
        converter.convert("${Thing.SOME_OTHER_THING.name()}", Thing.class, false) == Thing.SOME_OTHER_THING
    }

    def "cannot convert arbitrary type to Enum"() {
        when:
        def cl = {}
        converter.convert(cl, Thing.class, false)

        then:
        def e = thrown(UnsupportedNotationException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot convert the provided notation to an object of type Thing: ${cl.toString()}.
The following types/formats are supported:
  - One of the following values: 'SOME_THING', 'SOME_OTHER_THING'""")
    }

    def "cannot convert arbitrary string to Enum"() {
        when:
        converter.convert("not-a-thing", Thing.class, false)

        then:
        def e = thrown(TypeConversionException)
        e.message == TextUtil.toPlatformLineSeparators("Cannot convert string value 'not-a-thing' to an enum value of type '${Thing.name}' (valid case insensitive values: SOME_THING, SOME_OTHER_THING)")
    }
}
