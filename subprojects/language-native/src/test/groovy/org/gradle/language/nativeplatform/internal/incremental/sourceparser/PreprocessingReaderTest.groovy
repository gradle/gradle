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

class PreprocessingReaderTest extends Specification {
    private static final String BN = "\\" + System.getProperty("line.separator")
    String input

    def getOutput() {
        def reader = new PreprocessingReader(new StringReader(input))
        def result = new StringBuilder()
        def line = new StringBuilder()
        boolean first = true
        while (reader.readNextLine(line)) {
            if (first) {
                first = false
            } else {
                result.append('\n')
            }
            result.append(line.toString())
            line.setLength(0)
        }
        return result.toString()
    }

    def "reads from empty text"() {
        expect:
        def reader = new PreprocessingReader(new StringReader(""))
        !reader.readNextLine(new StringBuilder())
    }

    def "reads single line"() {
        expect:
        def reader = new PreprocessingReader(new StringReader("abc(123)"))
        def result = new StringBuilder()
        reader.readNextLine(result)
        result.toString() == "abc(123)"
        !reader.readNextLine(result)
    }

    def "reads multiple lines"() {
        expect:
        def reader = new PreprocessingReader(new StringReader("""
line 1

line 2""".replace('\n', eol)))
        def result = new StringBuilder()
        reader.readNextLine(result)
        result.toString() == ""

        result.setLength(0)
        reader.readNextLine(result)
        result.toString() == "line 1"

        result.setLength(0)
        reader.readNextLine(result)
        result.toString() == ""


        result.setLength(0)
        reader.readNextLine(result)
        result.toString() == "line 2"

        !reader.readNextLine(result)

        where:
        eol << ['\n', '\r', '\r\n']
    }

    def "consumes quoted strings"() {
        when:
        input = '''
"abc\\""
"\\""
'''

        then:
        output == '\n"abc\\""\n"\\""'
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
        output == "\nHere is a string that contains several inline comments."
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
        output == "\nHere is a \nstring\n\nwith interspersed \nline comments."
    }

    def "can cope with multiple unescaped and escaped \\r characters"() {
        when:
        input = "Here \r\r\\\r\\\r${BN}\\\r\\\r\\\r\\\r."
        then:
        output == "Here \n\n\\\n\\\n\\\n\\\n\\\n\\\n."
    }

    def "replaces #description at the start of content"() {
        when:
        input = testIn

        then:
        output == testOut

        where:
        description | testIn | testOut
        "line comment" | "// Comment on first line\nAnother line" | "\nAnother line"
        "inline comment" | "/* inline comment at the start */of the line" | " of the line"
        "line continuation" | "${BN} at the start of the content" | " at the start of the content"
    }
}
