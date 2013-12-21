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

import org.gradle.api.InvalidUserDataException
import spock.lang.Specification

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class NotationParserBuilderSpec extends Specification {

    def "adds just return parser as default"(){
        when:
        def parser = new NotationParserBuilder<String>()
                .resultingType(String.class).toComposite()
        then:
        "Some String" == parser.parseNotation("Some String")
    }

    def "can build with no just return parser"(){
        setup:
        def parser = new NotationParserBuilder<String>()
                .resultingType(String.class)
                .withDefaultJustReturnParser(false).toComposite()
        when:
        parser.parseNotation("Some String")
        then:
        def e = thrown(InvalidUserDataException)
        e.message == toPlatformLineSeparators("""Cannot convert the provided notation to an object of type String: Some String.
The following types/formats are supported:""")
    }

}
