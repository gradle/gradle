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

import org.junit.Test
import org.gradle.internal.SystemProperties

import static org.hamcrest.CoreMatchers.*
import static org.junit.Assert.*

class LineFilterTest {
    @Test void testEmptyInput() {
        def input = new StringReader("")
        def lineCount = 1
        def filter = new LineFilter(input, { "${lineCount++} - $it" as String })

        assertThat(filter.text, equalTo(""))
    }

    @Test void testEmptyLinesWithTrailingEOL() {
        def input = new StringReader("\n\n")
        def lineCount = 1
        def filter = new LineFilter(input, { "${lineCount++} - $it" as String })

        assertThat(filter.text, equalTo(lines("1 - ", "2 - ", "")))
    }
    
    @Test void testSingleLine() {
        def input = new StringReader("one")
        def lineCount = 1
        def filter = new LineFilter(input, { "${lineCount++} - $it" as String })

        assertThat(filter.text, equalTo("1 - one"))
    }

    @Test void testWithEmptyReplacementString() {
        def input = new StringReader("one")
        def filter = new LineFilter(input, {""})

        assertThat(filter.text, equalTo(""))
    }
    
    @Test void testCRLFWithTrailingEOL() {
        def input = new StringReader("one\r\ntwo\r\nthree\r\n")
        def lineCount = 1
        def filter = new LineFilter(input,  { "${lineCount++} - $it" as String })

        assertThat(filter.text, equalTo(lines("1 - one", "2 - two", "3 - three", "")))
    }

    @Test void testLfWithNoTrailingEOL() {
        def input = new StringReader("one\ntwo\nthree")
        def lineCount = 1
        def filter = new LineFilter(input,  { "${lineCount++} - $it" as String })

        assertThat(filter.text, equalTo(lines("1 - one", "2 - two", "3 - three")))
    }
    
    @Test void testCRWithNoTrailingEOL() {
        def input = new StringReader("one\rtwo\rthree")
        def lineCount = 1
        def filter = new LineFilter(input, { "${lineCount++} - $it" as String })

        assertThat(filter.text, equalTo(lines("1 - one", "2 - two", "3 - three")))
    }

    @Test void testClosureReturningNull() {
        def input = new StringReader("one\ntwo\nthree\n")
        def lineCount = 1
        def filter = new LineFilter(input,  { lineCount++ % 2 == 0 ? null : it })

        assertThat(filter.text, equalTo(lines("one", "three", "")))
    }

    @Test void testClosureAlwaysReturningNull() {
        def input = new StringReader("one\ntwo\nthree\n")
        def lineCount = 1
        def filter = new LineFilter(input,  { null })

        assertThat(filter.text, equalTo(lines()))
    }

    private String lines(String ... lines) {
        (lines as List).join(SystemProperties.instance.lineSeparator)
    }
}
