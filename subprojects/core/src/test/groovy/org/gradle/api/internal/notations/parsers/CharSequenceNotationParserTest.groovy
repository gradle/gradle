/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.notations.parsers

import spock.lang.Specification

class CharSequenceNotationParserTest extends Specification {
    def parser = new CharSequenceNotationParser()

    def "handles Strings"() {
        expect:
        converts("abc", "abc")
    }

    def "handles GStrings"() {
        expect:
        def foo = "abc"
        converts("$foo", "abc")
    }

    def "handles StringBuilders"() {
        def builder = new StringBuilder()
        builder.append("abc")

        expect:
        converts(builder, "abc")
    }

    void converts(from, to) {
        assert parser.parseNotation(from).getClass() == String
        assert parser.parseNotation(from) == to
    }
}
