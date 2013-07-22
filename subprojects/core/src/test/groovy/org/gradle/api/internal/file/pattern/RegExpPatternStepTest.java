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
package org.gradle.api.internal.file.pattern;

import org.junit.Test;

import static org.junit.Assert.*;

public class RegExpPatternStepTest {

    private void testPatternEscape(String expected, String pattern) {
        assertEquals(expected, RegExpPatternStep.getRegExPattern(pattern));
    }


    @Test public void testGetRegExpPattern() {
        testPatternEscape("literal", "literal");
        testPatternEscape("dotq.?", "dotq?");
        testPatternEscape("star.*stuff", "star*stuff");
        testPatternEscape("\\\\\\[\\]\\^\\-\\&\\.\\{\\}\\(\\)\\$\\+\\|\\<\\=\\!", "\\[]^-&.{}()$+|<=!");
        testPatternEscape("\\$\\&time", "$&time");
    }

    @Test public void testEscapeSet() {
        String testChars = "`~!@#$%^&*()-_=+[]{}\\|;:'\"<>,/";
        RegExpPatternStep step = new RegExpPatternStep(testChars, true);
        assertTrue(step.matches(testChars, true));
    }

    @Test public void testMatches() {
        RegExpPatternStep step = new RegExpPatternStep("literal", true);
        assertTrue(step.matches("literal", true));
        assertFalse(step.matches("Literal", true));
        assertFalse(step.matches("literally", true));
        assertFalse(step.matches("aliteral", true));

        step = new RegExpPatternStep("a?c", true);
        assertTrue(step.matches("abc", true));
        assertFalse(step.matches("abcd", true));
        assertTrue(step.matches("a$c", true));

        step = new RegExpPatternStep("a*c", true);
        assertTrue(step.matches("abc", true));
        assertTrue(step.matches("abrac", true));
        assertFalse(step.matches("abcd", true));

        step = new RegExpPatternStep("*", true);
        assertTrue(step.matches("asd;flkj", true));
    }


    @Test public void testCase() {
        RegExpPatternStep step = new RegExpPatternStep("MiXeD", true);
        assertTrue(step.matches("MiXeD", true));
        assertFalse(step.matches("mixed", true));

        step = new RegExpPatternStep("MiXeD", false);
        assertTrue(step.matches("MiXeD", true));
        assertTrue(step.matches("mixed", true));
    }
}
