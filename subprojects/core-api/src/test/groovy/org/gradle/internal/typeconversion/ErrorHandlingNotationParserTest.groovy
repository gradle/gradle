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
package org.gradle.internal.typeconversion

import org.gradle.internal.exceptions.DiagnosticsVisitor
import spock.lang.Specification

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class ErrorHandlingNotationParserTest extends Specification {
    def NotationParser<String, String> target = Mock()
    def parser = new ErrorHandlingNotationParser<String, String>("a thing", "<broken>", false, target)

    def "reports unable to parse null"() {
        when:
        parser.parseNotation(null)

        then:
        UnsupportedNotationException e = thrown()
        e.message == toPlatformLineSeparators('''Cannot convert a null value to a thing.
The following types/formats are supported:
  - format 1
  - format 2

<broken>''')

        1 * target.describe(!null) >> { DiagnosticsVisitor visitor -> visitor.candidate("format 1"); visitor.candidate("format 2") }
        0 * target._  //no parsing
    }

    def "reports unable to parse non-null"() {
        given:
        target.parseNotation("bad") >> { throw new UnsupportedNotationException("broken-part") }
        target.describe(!null) >> { DiagnosticsVisitor visitor -> visitor.candidate("format 1"); visitor.candidate("format 2") }

        when:
        parser.parseNotation("bad")

        then:
        UnsupportedNotationException e = thrown()
        e.message == toPlatformLineSeparators('''Cannot convert the provided notation to a thing: broken-part.
The following types/formats are supported:
  - format 1
  - format 2

<broken>''')

    }
}
