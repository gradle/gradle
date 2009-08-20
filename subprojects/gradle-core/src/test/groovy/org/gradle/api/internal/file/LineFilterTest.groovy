package org.gradle.api.internal.file

import org.junit.Test
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class LineFilterTest {
    @Test public void testCRLF() {
        Reader input = new StringReader("one\r\ntwo\r\nthree\r\n");
        int lineCount = 1;
        LineFilter filter = new LineFilter(input) { "${lineCount++} - $it" as String }

        Iterator<String> lines = getResults(filter).readLines().iterator()
        assertThat(lines.next(), startsWith("1 - one"))
        assertThat(lines.next(), startsWith("2 - two"))
        assertThat(lines.next(), startsWith("3 - three"))
    }

    @Test public void testLf() {
        Reader input = new StringReader("one\ntwo\nthree");
        int lineCount = 1;
        LineFilter filter = new LineFilter(input) { "${lineCount++} - $it" as String }

        Iterator<String> lines = getResults(filter).readLines().iterator()
        assertThat(lines.next(), startsWith("1 - one"))
        assertThat(lines.next(), startsWith("2 - two"))
        assertThat(lines.next(), startsWith("3 - three"))
    }

    private String getResults(Reader filter) {
        StringBuilder result = new StringBuilder()
        int nextChar = 0;
        while (nextChar != -1){
            nextChar = filter.read();
            if (nextChar != -1) {
                result.append((char)nextChar)
            }
        }
        return result.toString()
    }
}