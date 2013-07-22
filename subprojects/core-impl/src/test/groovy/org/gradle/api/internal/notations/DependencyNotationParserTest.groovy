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

package org.gradle.api.internal.notations;


import org.gradle.api.GradleException
import org.gradle.api.internal.notations.api.NotationParser
import spock.lang.Specification

public class DependencyNotationParserTest extends Specification {

    def notationParser = Mock(NotationParser)
    def parser = new DependencyNotationParser(notationParser);

    def "consumes notation and forwards gradle exception"() {
        given:
        def exc = new GradleException("Hey!")
        notationParser.parseNotation("foo") >> { throw exc }

        when:
        parser.parseNotation("foo")

        then:
        def e = thrown(Exception)
        exc == e
    }

    def "wraps programmer error with higher level exception"() {
        given:
        def exc = new RuntimeException("ka-boom!")
        notationParser.parseNotation("foo") >> { throw exc }

        when:
        parser.parseNotation("foo")

        then:
        def e = thrown(GradleException)
        exc == e.cause
    }
}
