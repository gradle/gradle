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
import spock.lang.Specification

class DefaultTypeConverterTest extends Specification {
    def converter = new DefaultTypeConverter(Stub(FileResolver))

    def "converts null to null"() {
        expect:
        converter.convert(null, Long.class, false) == null
        converter.convert(null, File.class, false) == null
        converter.convert(null, String.class, false) == null
        converter.convert(null, Thing.class, false) == null
    }

    def "converts int to Long"() {
        expect:
        converter.convert(123 as int, Long.class, false) == 123L
    }

    def "converts int to primitive Long"() {
        expect:
        converter.convert(123 as int, Long.class, true) == 123L
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

    def "converts Long to Long"() {
        expect:
        converter.convert(123L, Long.class, false) == 123L
    }

    def "converts Long to primitive long"() {
        expect:
        converter.convert(123L, Long.class, true) == 123L
    }

    def "cannot convert arbitrary type to Long"() {
        when:
        def cl = {}
        converter.convert(cl, Long.class, false)

        then:
        def e = thrown(UnsupportedNotationException)
        e.message == """Cannot convert the provided notation to an object of type Long: ${cl.toString()}.
The following types/formats are supported:
  - String or CharSequence instances.
  - Number instances."""
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
        e.message == """Cannot convert the provided notation to an object of type Boolean: ${cl.toString()}.
The following types/formats are supported:
  - String or CharSequence instances."""
    }

    def "converts CharSequence to String"() {
        expect:
        converter.convert("123", String.class, false) == "123"
        converter.convert(new StringBuilder("123"), String.class, false) == "123"
    }

    def "converts scalar types to String"() {
        expect:
        converter.convert(123L, String.class, false) == "123"
        converter.convert(true, String.class, false) == "true"
    }

    def "cannot convert arbitrary type to String"() {
        when:
        def cl = {}
        converter.convert(cl, String.class, false)

        then:
        def e = thrown(UnsupportedNotationException)
        e.message == """Cannot convert the provided notation to a String: ${cl.toString()}.
The following types/formats are supported:
  - String or CharSequence instances.
  - Any number or boolean."""
    }

    enum Thing { SOME_THING, SOME_OTHER_THING }

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
        e.message == """Cannot convert the provided notation to an object of type Thing: ${cl.toString()}.
The following types/formats are supported:
  - String or CharSequence instances."""
    }
}
