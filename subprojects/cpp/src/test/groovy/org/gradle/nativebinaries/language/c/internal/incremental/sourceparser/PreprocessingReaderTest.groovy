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

package org.gradle.nativebinaries.language.c.internal.incremental.sourceparser

import spock.lang.Specification

class PreprocessingReaderTest extends Specification {
    static String bn = "\\\r" + System.lineSeparator()
    String input

    def getOutput() {
        def reader = new PreprocessingReader(new StringReader(input))
        return reader.text.trim()
    }

    def "removes line continuation characters"() {
        when:
        input = """Here is a ${bn}single line ${bn}with continuations \\${bn}and \\ slashes\\\t\n too\\ \n."""

        then:
        output == "Here is a single line with continuations \\and \\ slashes\\\t\n too\\ \n."
    }

    def "replaces inline comments with space"() {
        when:
        input = """
Here/* comment */is a string/*
multiline
comment
here */that contains/**
 comment /* containing ** / ${bn} characters */several inline comments.
"""

        then:
        output == "Here is a string that contains several inline comments."
    }

    def "replaces line comments with space"() {
        when:
        input = """
Here is a // comment
string
// Line comment
with interspersed // comment /* with inline comment */ and // other comment */ chars
line comments.
"""

        then:
        output == "Here is a \nstring\n\nwith interspersed \nline comments."
    }

    def "can cope with multiple unescaped and escaped \\r characters"() {
        when:
        input =  "Here \r\r\\\r\\\r${bn}\\\r\\\r\\\r\\\r."
        then:
        output == "Here \r\r\\\r\\\r\\\r\\\r\\\r\\\r."
    }
}
