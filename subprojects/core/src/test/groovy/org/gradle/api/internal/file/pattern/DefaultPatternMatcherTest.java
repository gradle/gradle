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

import org.gradle.api.file.RelativePath;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class DefaultPatternMatcherTest {
    private DefaultPatternMatcher matcher;
    private RelativePath path;


    @Test
    public void testParsing() {
        List<PatternStep> steps;
        PatternStep step;

        // parse forward slash pattern
        matcher = new DefaultPatternMatcher(true, true, "a", "b", "c");
        steps = matcher.getStepsForTest();
        assertEquals(3, steps.size());
        step = steps.get(2);
        assertTrue(step.matches("c"));
        //assertFalse(step.matches("c", false));

        // try matching a wrong literal string
        assertFalse(step.matches("somethingelse"));

        // check greedy
        matcher = new DefaultPatternMatcher(true, true, "a", "**", "c");
        steps = matcher.getStepsForTest();
        step = steps.get(1);
        assertTrue(step.isGreedy());
    }

    @Test
    public void testEmpty() {
        DefaultPatternMatcher matcher = new DefaultPatternMatcher(true, true);
        List<PatternStep> steps = matcher.getStepsForTest();
        assertEquals(0, steps.size());

        // both empty
        assertTrue(matcher.isSatisfiedBy(file()));

        // empty matcher, non-empty path
        assertFalse(matcher.isSatisfiedBy(file("a")));

        // non-empty matcher, empty path
        matcher = new DefaultPatternMatcher(true, true, "a");
        assertFalse(matcher.isSatisfiedBy(file()));
    }

    @Test
    public void testLiterals() {
        matcher = new DefaultPatternMatcher(true, true, "a");
        assertTrue(matcher.isSatisfiedBy(file("a")));
        assertFalse(matcher.isSatisfiedBy(file("b")));

        matcher = new DefaultPatternMatcher(true, true, "a", "b");
        assertTrue(matcher.isSatisfiedBy(file("a", "b")));
        assertFalse(matcher.isSatisfiedBy(file("a", "c")));
        assertFalse(matcher.isSatisfiedBy(file("b", "c")));

        // short path
        assertFalse(matcher.isSatisfiedBy(file("a")));

        // long path
        assertFalse(matcher.isSatisfiedBy(file("a", "b", "c")));
    }

    @Test
    public void testPartials() {
        matcher = new DefaultPatternMatcher(true, true, "a", "b", "c");
        assertTrue(matcher.isSatisfiedBy(dir("a", "b")));
        assertFalse(matcher.isSatisfiedBy(file("a", "b")));

        matcher = new DefaultPatternMatcher(false, true, "a", "b", "c");
        assertFalse(matcher.isSatisfiedBy(dir("a", "b")));
    }

    @Test
    public void testWildCards() {
        matcher = new DefaultPatternMatcher(true, true, "*");
        assertTrue(matcher.isSatisfiedBy(file("anything")));
        assertFalse(matcher.isSatisfiedBy(file("anything", "b")));

        matcher = new DefaultPatternMatcher(true, true, "any??ing");
        assertTrue(matcher.isSatisfiedBy(file("anything")));
    }

    @Test
    public void testGreedy() {
        matcher = new DefaultPatternMatcher(true, true, "a", "**");
        assertTrue(matcher.isSatisfiedBy(file("a", "b", "c")));

        //leading greedy
        matcher = new DefaultPatternMatcher(true, true, "**", "c");
        assertTrue(matcher.isSatisfiedBy(file("a", "b", "c")));
        assertFalse(matcher.isSatisfiedBy(file("a", "b", "d")));

        // inner greedy
        matcher = new DefaultPatternMatcher(true, true, "a", "**", "c");
        assertTrue(matcher.isSatisfiedBy(file("a", "b", "c")));
        assertTrue(matcher.isSatisfiedBy(file("a", "aa", "bb", "c")));

        assertTrue(matcher.isSatisfiedBy(dir("a")));
        assertTrue(matcher.isSatisfiedBy(dir("a", "b")));
        assertTrue(matcher.isSatisfiedBy(dir("a", "aa", "bb", "d")));
        assertTrue(matcher.isSatisfiedBy(dir("a", "c")));
        assertTrue(matcher.isSatisfiedBy(dir("a", "b", "c")));
        assertTrue(matcher.isSatisfiedBy(dir("a", "c", "d")));
        assertTrue(matcher.isSatisfiedBy(dir("a", "b", "c", "d")));

        assertFalse(matcher.isSatisfiedBy(file("a", "aa", "bb", "d")));

        assertFalse(matcher.isSatisfiedBy(dir("b", "c")));

        // fake trail
        matcher = new DefaultPatternMatcher(true, true, "a", "**", "c", "d");
        assertTrue(matcher.isSatisfiedBy(file("a", "b", "c", "e", "c", "d")));

        // multiple greedies
        matcher = new DefaultPatternMatcher(true, true, "a", "**", "c", "**", "e");
        assertTrue(matcher.isSatisfiedBy(file("a", "b", "c", "d", "e")));
        assertTrue(matcher.isSatisfiedBy(file("a", "b", "bb", "c", "d", "e")));
        assertTrue(matcher.isSatisfiedBy(file("a", "q", "bb", "c", "d", "c", "d", "e")));

        // Missing greedy
        matcher = new DefaultPatternMatcher(true, true, "a", "**", "c");
        path = new RelativePath(true, "a", "c");
        assertTrue(matcher.isSatisfiedBy(file("a", "c")));
        assertFalse(matcher.isSatisfiedBy(file("a", "d")));
    }

    @Test
    public void testTypical() {
        matcher = new DefaultPatternMatcher(true, true, "**", "CVS", "*");
        assertTrue(matcher.isSatisfiedBy(file("CVS", "Repository")));
        assertTrue(matcher.isSatisfiedBy(file("org", "gradle", "CVS", "Entries")));
        assertFalse(matcher.isSatisfiedBy(file("org", "gradle", "CVS", "foo", "bar", "Entries")));

        matcher = new DefaultPatternMatcher(true, true, "src", "main", "**");
        assertTrue(matcher.isSatisfiedBy(file("src", "main", "groovy", "org")));
        assertFalse(matcher.isSatisfiedBy(file("src", "test", "groovy", "org")));

        matcher = new DefaultPatternMatcher(true, true, "**", "test", "**");
        assertTrue(matcher.isSatisfiedBy(file("src", "main", "test")));
        assertTrue(matcher.isSatisfiedBy(file("src", "test", "main")));
        assertTrue(matcher.isSatisfiedBy(file("test", "main")));

        assertFalse(matcher.isSatisfiedBy(file("src", "main", "fred")));
    }

    @Test
    public void testCase() {
        matcher = new DefaultPatternMatcher(true, true, "a", "b");
        assertTrue(matcher.isSatisfiedBy(file("a", "b")));
        assertFalse(matcher.isSatisfiedBy(file("A", "B")));

        matcher = new DefaultPatternMatcher(true, false, "a", "b");
        assertTrue(matcher.isSatisfiedBy(file("a", "b")));
        assertTrue(matcher.isSatisfiedBy(file("A", "B")));
    }

    RelativePath file(String... path) {
        return new RelativePath(true, path);
    }

    RelativePath dir(String... path) {
        return new RelativePath(false, path);
    }
}
