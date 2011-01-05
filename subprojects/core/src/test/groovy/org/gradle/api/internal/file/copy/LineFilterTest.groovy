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
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class LineFilterTest {
    @Test public void testEmptyInput() {
        Reader input = new StringReader("");
        int lineCount = 1;
        LineFilter filter = new LineFilter(input, { "${lineCount++} - $it" as String })

        assertThat(filter.text, equalTo(""))
    }

    @Test public void testEmptyLinesWithTrailingEOL() {
        Reader input = new StringReader("\n\n");
        int lineCount = 1;
        LineFilter filter = new LineFilter(input, { "${lineCount++} - $it" as String })

        assertThat(filter.text, equalTo(lines("1 - ", "2 - ", "")))
    }
    
    @Test public void testSingleLine() {
        Reader input = new StringReader("one");
        int lineCount = 1;
        LineFilter filter = new LineFilter(input, { "${lineCount++} - $it" as String })

        assertThat(filter.text, equalTo("1 - one"))
    }
    
    @Test public void testCRLFWithTrailingEOL() {
        Reader input = new StringReader("one\r\ntwo\r\nthree\r\n");
        int lineCount = 1;
        LineFilter filter = new LineFilter(input,  { "${lineCount++} - $it" as String })

        assertThat(filter.text, equalTo(lines("1 - one", "2 - two", "3 - three", "")))
    }

    @Test public void testLfWithNoTrailingEOL() {
        Reader input = new StringReader("one\ntwo\nthree");
        int lineCount = 1;
        LineFilter filter = new LineFilter(input,  { "${lineCount++} - $it" as String })

        assertThat(filter.text, equalTo(lines("1 - one", "2 - two", "3 - three")))
    }
    
    @Test public void testCRWithNoTrailingEOL() {
        Reader input = new StringReader("one\rtwo\rthree");
        int lineCount = 1;
        LineFilter filter = new LineFilter(input, { "${lineCount++} - $it" as String })

        assertThat(filter.text, equalTo(lines("1 - one", "2 - two", "3 - three")))
    }

    private String lines(String ... lines) {
        return (lines as List).join(System.getProperty('line.separator'))
    }
}