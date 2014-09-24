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

package org.gradle.language.nativeplatform.internal.incremental.sourceparser

import spock.lang.Specification
import spock.lang.Unroll

class PreprocessingReaderTest extends Specification {
    private static final String BN = "\\" + System.getProperty("line.separator")
    String input

    def getOutput() {
        def reader = new PreprocessingReader(new StringReader(input))
        return reader.text
    }

    def "removes line continuation characters"() {
        when:
        input = """Here is a ${BN}single line ${BN}with continuations \\${BN}and \\ slashes\\\t\n too\\ \n."""

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
 comment /* containing ** / ${BN} characters */several inline comments.
"""

        then:
        output == "\nHere is a string that contains several inline comments.\n"
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
        output == "\nHere is a \nstring\n\nwith interspersed \nline comments.\n"
    }

    def "can cope with multiple unescaped and escaped \\r characters"() {
        when:
        input = "Here \r\r\\\r\\\r${BN}\\\r\\\r\\\r\\\r."
        then:
        output == "Here \r\r\\\r\\\r\\\r\\\r\\\r\\\r."
    }

    @Unroll
    def "replaces #description at the start of content"() {
        when:
        input = testIn

        then:
        output == testOut

        where:
        description | testIn | testOut
        "line comment" | "// Commment on first line\nAnother line" | "\nAnother line"
        "inline comment" | "/* inline comment at the start */of the line" | " of the line"
        "line continuation" | "${BN} at the start of the content" | " at the start of the content"
    }
}
