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

package org.gradle.internal.typeconversion

import spock.lang.Specification

class NotationConverterToNotationParserAdapterTest extends Specification {
    def converter = Mock(NotationConverter)
    def parser = new NotationConverterToNotationParserAdapter(converter)

    def "converts notation"() {
        given:
        converter.convert(12, _) >> { Object notation, NotationConvertResult result -> result.converted("12") }

        expect:
        parser.parseNotation(12) == "12"
    }

    def "can convert to null value"() {
        given:
        converter.convert(12, _) >> { Object notation, NotationConvertResult result -> result.converted(null) }

        expect:
        parser.parseNotation(12) == null
    }

    def "fails when the notation cannot be converted"() {
        given:
        converter.convert(12, _) >> { }

        when:
        parser.parseNotation(12)

        then:
        UnsupportedNotationException e = thrown()
    }
}
