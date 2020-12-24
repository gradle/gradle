/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.internal.typeconversion;


import spock.lang.Specification

public class TypedNotationConverterTest extends Specification {

    def parser = NotationParserBuilder.toType(Integer).converter(new DummyConverter()).toComposite()

    def "parses object of source type"(){
        expect:
        parser.parseNotation("100") == 100
    }

    def "throws meaningful exception on parse attempt"(){
        when:
        parser.parseNotation(new Object())

        then:
        thrown(UnsupportedNotationException)
    }

    class DummyConverter extends TypedNotationConverter<String, Integer> {

        DummyConverter() {
            super(String.class)
        }

        Integer parseType(String notation) {
            return Integer.parseInt(notation);
        }
    }
}
