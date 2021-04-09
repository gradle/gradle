/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.internal.exceptions.DiagnosticsVisitor
import spock.lang.Specification

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class NotationParserBuilderSpec extends Specification {

    def "adds just return parser as default"() {
        when:
        def parser = NotationParserBuilder.toType(String.class).toComposite()

        then:
        "Some String" == parser.parseNotation("Some String")
    }

    def "can add a converter"() {
        def converter = Mock(NotationConverter)

        given:
        converter.convert(_, _) >> { Object n, NotationConvertResult result -> result.converted("[${n}]") }

        and:
        def parser = NotationParserBuilder.toType(String.class).converter(converter).toComposite()

        expect:
        parser.parseNotation(12) == "[12]"
    }

    def "can add multiple converters"() {
        def converter1 = Mock(NotationConverter)
        def converter2 = Mock(NotationConverter)

        given:
        _ * converter1.convert(12, _) >> { Object n, NotationConvertResult result -> result.converted("[12]") }
        converter2.convert(true, _) >> { Object n, NotationConvertResult result -> result.converted("True") }

        and:
        def parser = NotationParserBuilder.toType(String.class).converter(converter1).converter(converter2).toComposite()

        expect:
        parser.parseNotation(12) == "[12]"
        parser.parseNotation(true) == "True"
    }

    def "can add a converter that converts notations of a given type"() {
        def converter = Mock(NotationConverter)

        given:
        converter.convert(_, _) >> { Number n, NotationConvertResult result -> result.converted("[${n}]") }

        and:
        def parser = NotationParserBuilder.toType(String.class).fromType(Number, converter).toComposite()

        expect:
        parser.parseNotation(12) == "[12]"
    }

    def "can add a converter that converts CharSequence notations"() {
        def converter = Mock(NotationConverter)

        given:
        converter.convert(_, _) >> { String n, NotationConvertResult result -> result.converted("[${n}]") }

        and:
        def parser = NotationParserBuilder.toType(String.class).fromCharSequence(converter).toComposite()

        expect:
        parser.parseNotation(new StringBuilder("12")) == "[12]"
    }

    def "can add a converter that converts CharSequence notations when the target type is String"() {
        def parser = NotationParserBuilder.toType(String.class).fromCharSequence().toComposite()

        expect:
        parser.parseNotation(new StringBuilder("12")) == "12"
        parser.parseNotation("12") == "12"
    }

    def "can opt in to allow null as input"() {
        def converter = Mock(NotationConverter)
        def parser = NotationParserBuilder
                .toType(String.class)
                .allowNullInput()
                .converter(converter)
                .toComposite()


        given:
        converter.convert(null, _) >> { Object n, NotationConvertResult result -> result.converted("[null]") }

        expect:
        parser.parseNotation(null) == "[null]"
    }

    def "reports available formats when accepts any notation type"() {
        def converter = Mock(NotationConverter)

        given:
        converter.describe(_) >> { DiagnosticsVisitor visitor -> visitor.candidate("a string containing a greeting").example("'hello world'") }
        def parser = NotationParserBuilder.toType(Number).fromType(String, converter).toComposite()

        when:
        parser.parseNotation(false)

        then:
        UnsupportedNotationException e = thrown()
        e.message == toPlatformLineSeparators("""Cannot convert the provided notation to an object of type Number: false.
The following types/formats are supported:
  - Instances of Number.
  - a string containing a greeting, for example 'hello world'.""")
    }

    def "reports available formats when accepts notation type that is not assignable to target type"() {
        def converter = Mock(NotationConverter)

        given:
        converter.describe(_) >> { DiagnosticsVisitor visitor -> visitor.candidate("a string containing a greeting").example("'hello world'") }
        def parser = NotationParserBuilder.builder(String, Number).fromType(String, converter).toComposite()

        when:
        parser.parseNotation("broken")

        then:
        UnsupportedNotationException e = thrown()
        e.message == toPlatformLineSeparators("""Cannot convert the provided notation to an object of type Number: broken.
The following types/formats are supported:
  - a string containing a greeting, for example 'hello world'.""")
    }

    def "can tweak the conversion error messages"() {
        given:
        def parser = NotationParserBuilder.toType(String.class).typeDisplayName("a thing").toComposite()

        when:
        parser.parseNotation(12)

        then:
        UnsupportedNotationException e = thrown()
        e.message == toPlatformLineSeparators("""Cannot convert the provided notation to a thing: 12.
The following types/formats are supported:
  - Instances of String.""")
    }

    def "uses nice display name for String target type"() {
        given:
        def parser = NotationParserBuilder.toType(String.class).toComposite()

        when:
        parser.parseNotation(12)

        then:
        UnsupportedNotationException e = thrown()
        e.message == toPlatformLineSeparators("""Cannot convert the provided notation to a String: 12.
The following types/formats are supported:
  - Instances of String.""")
    }
}
