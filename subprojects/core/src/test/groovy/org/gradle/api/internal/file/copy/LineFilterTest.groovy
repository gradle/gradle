/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.copy

import spock.lang.Specification

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat

class LineFilterTest extends Specification {
    void testEmptyInput() {
        def input = new StringReader("")
        def lineCount = 1
        def filter = new LineFilter(input, { "${lineCount++} - $it" as String })

        expect:
        assertThat(filter.text, equalTo(""))
    }

    void "testEmptyLines with trailing #platform EOL"() {
        def input = new StringReader("$originalEol$originalEol")
        def lineCount = 1
        def filter = new LineFilter(input, { "${lineCount++} - $it" as String })

        expect:
        assertThat(filter.text, equalTo(linesWithEol(resultingEol, ["1 - ", "2 - ", ""])))

        where:
        platform            | originalEol   | resultingEol
        "Windows"           | "\r\n"        | "\r\n"
        "Linux"             | "\n"          | "\n"
        "Classic Mac OS"    | "\r"          | "\n"
    }

    void testSingleLine() {
        def input = new StringReader("one")
        def lineCount = 1
        def filter = new LineFilter(input, { "${lineCount++} - $it" as String })

        expect:
        assertThat(filter.text, equalTo("1 - one"))
    }

    void testWithEmptyReplacementString() {
        def input = new StringReader("one")
        def filter = new LineFilter(input, { "" })

        expect:
        assertThat(filter.text, equalTo(""))
    }

    void "test with trailing #platform EOL"() {
        def input = new StringReader("one${originalEol}two${originalEol}three${originalEol}")
        def lineCount = 1
        def filter = new LineFilter(input, { "${lineCount++} - $it" as String })

        expect:
        assertThat(filter.text, equalTo(linesWithEol(resultingEol, ["1 - one", "2 - two", "3 - three", ""])))

        where:
        platform            | originalEol   | resultingEol
        "Windows"           | "\r\n"        | "\r\n"
        "Linux"             | "\n"          | "\n"
        "Classic Mac OS"    | "\r"          | "\n"
    }

    void testMixedLineEndings() {
        def input = new StringReader("one\ntwo\r\nthree\nfour\r\n")
        def lineCount = 1
        def filter = new LineFilter(input, { "${lineCount++} - $it" as String })

        expect:
        assertThat(filter.text, equalTo("1 - one\n2 - two\r\n3 - three\n4 - four\r\n"))
    }

    void "test with no trailing #platform EOL"() {
        def input = new StringReader("one${originalEol}two${originalEol}three")
        def lineCount = 1
        def filter = new LineFilter(input, { "${lineCount++} - $it" as String })

        expect:
        assertThat(filter.text, equalTo(linesWithEol(resultingEol, ["1 - one", "2 - two", "3 - three"])))

        where:
        platform            | originalEol   | resultingEol
        "Windows"           | "\r\n"        | "\r\n"
        "Linux"             | "\n"          | "\n"
        "Classic Mac OS"    | "\r"          | "\n"
    }

    void "testClosureReturningNull with #platform EOL"() {
        def input = new StringReader("one${originalEol}two${originalEol}three${originalEol}")
        def lineCount = 1
        def filter = new LineFilter(input, { lineCount++ % 2 == 0 ? null : it })

        expect:
        assertThat(filter.text, equalTo(linesWithEol(resultingEol, ["one", "three", ""])))

        where:
        platform            | originalEol   | resultingEol
        "Windows"           | "\r\n"        | "\r\n"
        "Linux"             | "\n"          | "\n"
        "Classic Mac OS"    | "\r"          | "\n"
    }

    void "testClosureAlwaysReturningNull with #platform EOL"() {
        def input = new StringReader("one${originalEol}two${originalEol}three${originalEol}")
        def filter = new LineFilter(input, { null })

        expect:
        assertThat(filter.text, equalTo(""))

        where:
        platform            | originalEol   | resultingEol
        "Windows"           | "\r\n"        | "\r\n"
        "Linux"             | "\n"          | "\n"
        "Classic Mac OS"    | "\r"          | "\n"
    }

    private static String linesWithEol(String eol, List<String> lines) {
        return lines.join(eol)
    }
}
