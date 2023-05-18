/*
 * Copyright 2022 the original author or authors.
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

class IntegerFromCharSequenceNotationConverterSpec extends Specification {

    def parser = new NotationConverterToNotationParserAdapter(new IntegerFromCharSequenceNotationConverter())

    def "can convert strings to integers"() {
        expect:
        123 == parser.parseNotation("123")
    }

    def "reports available values for non convertible strings"() {
        when:
        parser.parseNotation(value)

        then:
        def e = thrown(TypeConversionException)
        e.message == "Cannot convert string value '$value' to an integer."

        where:
        value        | _
        "notANumber" | _
        "3.2"        | _
    }

}
