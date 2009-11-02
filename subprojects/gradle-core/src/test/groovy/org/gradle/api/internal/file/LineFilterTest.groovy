package org.gradle.api.internal.file

import org.junit.Test
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class LineFilterTest {
    @Test public void testEmptyInput() {
        Reader input = new StringReader("");
        int lineCount = 1;
        LineFilter filter = new LineFilter(input) { "${lineCount++} - $it" as String }

        assertThat(filter.text, equalTo(""))
    }

    @Test public void testEmptyLinesWithTrailingEOL() {
        Reader input = new StringReader("\n\n");
        int lineCount = 1;
        LineFilter filter = new LineFilter(input) { "${lineCount++} - $it" as String }

        assertThat(filter.text, equalTo(lines("1 - ", "2 - ", "")))
    }
    
    @Test public void testSingleLine() {
        Reader input = new StringReader("one");
        int lineCount = 1;
        LineFilter filter = new LineFilter(input) { "${lineCount++} - $it" as String }

        assertThat(filter.text, equalTo("1 - one"))
    }
    
    @Test public void testCRLFWithTrailingEOL() {
        Reader input = new StringReader("one\r\ntwo\r\nthree\r\n");
        int lineCount = 1;
        LineFilter filter = new LineFilter(input) { "${lineCount++} - $it" as String }

        assertThat(filter.text, equalTo(lines("1 - one", "2 - two", "3 - three", "")))
    }

    @Test public void testLfWithNoTrailingEOL() {
        Reader input = new StringReader("one\ntwo\nthree");
        int lineCount = 1;
        LineFilter filter = new LineFilter(input) { "${lineCount++} - $it" as String }

        assertThat(filter.text, equalTo(lines("1 - one", "2 - two", "3 - three")))
    }
    
    @Test public void testCRWithNoTrailingEOL() {
        Reader input = new StringReader("one\rtwo\rthree");
        int lineCount = 1;
        LineFilter filter = new LineFilter(input) { "${lineCount++} - $it" as String }

        assertThat(filter.text, equalTo(lines("1 - one", "2 - two", "3 - three")))
    }

    private String lines(String ... lines) {
        return (lines as List).join(System.getProperty('line.separator'))
    }
}