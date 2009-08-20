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
import org.gradle.api.internal.file.RelativePath;
import org.gradle.api.specs.Spec;

import java.util.List;

public class PatternMatcherFactoryTest {
    private Spec<RelativePath> matcher;
    private RelativePath path;
    List<PatternStep> steps;
    PatternStep step;

    @Test public void testSlashDirection() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "a/b/c");
        assertTrue(matcher instanceof DefaultPatternMatcher);
        steps = ((DefaultPatternMatcher) matcher).getStepsForTest();
        assertEquals(3, steps.size());
        step = steps.get(2);
        assertTrue(step.matches("c", true));


        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "a\\b\\c");
        assertTrue(matcher instanceof DefaultPatternMatcher);
        steps = ((DefaultPatternMatcher) matcher).getStepsForTest();
        assertEquals(3, steps.size());
        step = steps.get(2);
        assertTrue(step.matches("c", true));
    }

    /**
     * Test that trailing slash gets ** added automatically
     */
    @Test public void testAddGreedy() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "a/b/");
        assertTrue(matcher instanceof DefaultPatternMatcher);
        steps = ((DefaultPatternMatcher) matcher).getStepsForTest();
        assertEquals(3, steps.size());
        step = steps.get(2);
        assertTrue(step.isGreedy());

        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "a\\b\\");
        assertTrue(matcher instanceof DefaultPatternMatcher);
        steps = ((DefaultPatternMatcher) matcher).getStepsForTest();
        assertEquals(3, steps.size());
        step = steps.get(2);
        assertTrue(step.isGreedy());
    }

    @Test public void testNameOnly() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "**/*.jsp");
        assertTrue(matcher instanceof NameOnlyPatternMatcher);
        path = new RelativePath(true, "fred.jsp");
        assertTrue(matcher.isSatisfiedBy(path));
    }

    @Test public void testShortenedGreedy() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "**/");
        assertTrue(matcher instanceof DefaultPatternMatcher);
        steps = ((DefaultPatternMatcher) matcher).getStepsForTest();
        assertEquals(1, steps.size());
        step = steps.get(0);
        assertTrue(step.isGreedy());
    }
}
