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
import org.gradle.api.file.RelativePath;

import java.util.List;

public class DefaultPatternMatcherTest {
    private DefaultPatternMatcher matcher;
    private RelativePath path;


    @Test public void testParsing() {
        List<PatternStep> steps;
        PatternStep step;

        // parse forward slash pattern
        matcher = new DefaultPatternMatcher(true, true, "a", "b", "c");
        steps = matcher.getStepsForTest();
        assertEquals(3, steps.size());
        step = steps.get(2);
        assertTrue(step.matches("c", true));
        //assertFalse(step.matches("c", false));

        // try matching a wrong literal string
        assertFalse(step.matches("somethingelse", true));

        // check greedy
        matcher = new DefaultPatternMatcher(true, true, "a", "**", "c");
        steps = matcher.getStepsForTest();
        step = steps.get(1);
        assertTrue(step.isGreedy());
    }

    @Test public void testEmpty() {
        DefaultPatternMatcher matcher = new DefaultPatternMatcher(true, true);
        List<PatternStep> steps = matcher.getStepsForTest();
        assertEquals(0, steps.size());

        // both empty
        RelativePath path = new RelativePath(true);
        assertTrue(matcher.isSatisfiedBy(path));

        // empty matcher, non-empty path
        path = new RelativePath(true, "a");
        assertFalse(matcher.isSatisfiedBy(path));

        // non-empty matcher, empty path
        matcher = new DefaultPatternMatcher(true, true, "a");
        path = new RelativePath(true);
        assertFalse(matcher.isSatisfiedBy(path));

    }

    @Test public void testLiterals() {
        matcher = new DefaultPatternMatcher(true, true, "a");
        path = new RelativePath(true, "a");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "b");
        assertFalse(matcher.isSatisfiedBy(path));
        
        matcher = new DefaultPatternMatcher(true, true, "a", "b");
        path = new RelativePath(true, "a", "b");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "a", "c");
        assertFalse(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "b", "c");
        assertFalse(matcher.isSatisfiedBy(path));

        // short path
        path = new RelativePath(true, "a");
        assertFalse(matcher.isSatisfiedBy(path));

        // long path
        path = new RelativePath(true, "a", "b", "c");
        assertFalse(matcher.isSatisfiedBy(path));
    }

    @Test public void testPartials() {
        matcher = new DefaultPatternMatcher(true, true, "a", "b", "c");
        path = new RelativePath(false, "a", "b");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "a", "b");
        assertFalse(matcher.isSatisfiedBy(path));

        matcher = new DefaultPatternMatcher(false, true, "a", "b", "c");
        path = new RelativePath(false, "a", "b");
        assertFalse(matcher.isSatisfiedBy(path));

    }

    @Test public void testWildCards() {
        matcher = new DefaultPatternMatcher(true, true, "*");
        path = new RelativePath(true, "anything");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "anything", "b");
        assertFalse(matcher.isSatisfiedBy(path));

        matcher = new DefaultPatternMatcher(true, true, "any??ing");
        path = new RelativePath(true, "anything");
        assertTrue(matcher.isSatisfiedBy(path));
    }

    @Test public void testGreedy() {
        matcher = new DefaultPatternMatcher(true, true, "a", "**");
        path = new RelativePath(true, "a", "b", "c");
        assertTrue(matcher.isSatisfiedBy(path));

        //leading greedy
        matcher = new DefaultPatternMatcher(true, true, "**", "c");
        path = new RelativePath(true, "a", "b", "c");
        assertTrue(matcher.isSatisfiedBy(path));
        
        path = new RelativePath(true, "a", "b", "d");
        assertFalse(matcher.isSatisfiedBy(path));

        // inner greedy
        matcher = new DefaultPatternMatcher(true, true, "a", "**", "c");
        path = new RelativePath(true, "a", "b", "c");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "a", "aa", "bb", "c");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(false, "a", "aa", "bb", "d");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "a", "aa", "bb", "d");
        assertFalse(matcher.isSatisfiedBy(path));

        // fake trail
        matcher = new DefaultPatternMatcher(true, true, "a", "**", "c", "d");
        path = new RelativePath(true, "a", "b", "c", "e", "c", "d");
        assertTrue(matcher.isSatisfiedBy(path));
        
        // multiple greedies
        matcher = new DefaultPatternMatcher(true, true, "a", "**", "c", "**", "e");
        path = new RelativePath(true, "a", "b", "c", "d", "e");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "a", "b", "bb", "c", "d", "e");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "a", "q", "bb", "c", "d", "c", "d", "e");
        assertTrue(matcher.isSatisfiedBy(path));

        // Missing greedy
        matcher = new DefaultPatternMatcher(true, true, "a", "**", "c");
        path = new RelativePath(true, "a", "c");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "a", "d");
        assertFalse(matcher.isSatisfiedBy(path));
    }

    @Test public void testTypical() {
        matcher = new DefaultPatternMatcher(true, true, "**", "CVS", "*");
        path = new RelativePath(true, "CVS", "Repository");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "org", "gradle", "CVS", "Entries");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "org", "gradle", "CVS", "foo", "bar", "Entries");
        assertFalse(matcher.isSatisfiedBy(path));

        matcher = new DefaultPatternMatcher(true, true, "src", "main", "**");
        path = new RelativePath(true, "src", "main", "groovy", "org");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "src", "test", "groovy", "org");
        assertFalse(matcher.isSatisfiedBy(path));

        matcher = new DefaultPatternMatcher(true, true, "**", "test", "**");
        // below fails, trailing ** not ignored
        path = new RelativePath(true, "src", "main", "test");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "src", "test", "main");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "src", "main", "fred");
        assertFalse(matcher.isSatisfiedBy(path));
    }

    @Test public void testCase() {
        matcher = new DefaultPatternMatcher(true, true, "a", "b");
        assertTrue(matcher.isSatisfiedBy(new RelativePath(true, "a", "b")));
        assertFalse(matcher.isSatisfiedBy(new RelativePath(true, "A", "B")));

        matcher = new DefaultPatternMatcher(true, false, "a", "b");
        assertTrue(matcher.isSatisfiedBy(new RelativePath(true, "a", "b")));
        assertTrue(matcher.isSatisfiedBy(new RelativePath(true, "A", "B")));
    }

}
