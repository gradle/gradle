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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RegExpPatternStepTest {

    private void testPatternEscape(String expected, String pattern) {
        assertEquals(expected, RegExpPatternStep.getRegExPattern(pattern));
    }

    @Test public void testGetRegExpPattern() {
        testPatternEscape("literal", "literal");
        testPatternEscape("dotq.", "dotq?");
        testPatternEscape("star.*stuff", "star*stuff");
        testPatternEscape("\\\\\\[\\]\\^\\-\\&\\.\\{\\}\\(\\)\\$\\+\\|\\<\\=\\!", "\\[]^-&.{}()$+|<=!");
        testPatternEscape("\\$\\&time", "$&time");
    }

    @Test public void testEscapeSet() {
        String testChars = "`~!@#$%^&*()-_=+[]{}\\|;:'\"<>,/";
        RegExpPatternStep step = new RegExpPatternStep(testChars, true);
        assertTrue(step.matches(testChars));
    }

    @Test public void testLiteralMatches() {
        RegExpPatternStep step = new RegExpPatternStep("literal", true);
        assertTrue(step.matches("literal"));
        assertFalse(step.matches("Literal"));
        assertFalse(step.matches("literally"));
        assertFalse(step.matches("aliteral"));
    }

    @Test public void testSingleCharWildcard() {
        RegExpPatternStep step = new RegExpPatternStep("a?c", true);
        assertTrue(step.matches("abc"));
        assertTrue(step.matches("a$c"));
        assertTrue(step.matches("a?c"));

        assertFalse(step.matches("ac"));
        assertFalse(step.matches("abcd"));
        assertFalse(step.matches("abd"));
        assertFalse(step.matches("a"));
    }

    @Test public void testMultiCharWildcard() {
        RegExpPatternStep step = new RegExpPatternStep("a*c", true);
        assertTrue(step.matches("abc"));
        assertTrue(step.matches("abrac"));
        assertFalse(step.matches("abcd"));
        assertFalse(step.matches("ab"));
        assertFalse(step.matches("a"));

        step = new RegExpPatternStep("*", true);
        assertTrue(step.matches("asd;flkj"));
        assertTrue(step.matches(""));
    }

    @Test public void testCase() {
        RegExpPatternStep step = new RegExpPatternStep("MiXeD", true);
        assertTrue(step.matches("MiXeD"));
        assertFalse(step.matches("mixed"));

        step = new RegExpPatternStep("MiXeD", false);
        assertTrue(step.matches("MiXeD"));
        assertTrue(step.matches("mixed"));

        step = new RegExpPatternStep("MiXeD?", true);
        assertTrue(step.matches("MiXeD1"));
        assertFalse(step.matches("mixed1"));

        step = new RegExpPatternStep("MiXeD?", false);
        assertTrue(step.matches("MiXeD1"));
        assertTrue(step.matches("mixed1"));
    }
}
