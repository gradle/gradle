package org.gradle.api.internal.tasks.copy.pattern;

import org.junit.Test;
import static org.junit.Assert.*;
import org.gradle.api.internal.tasks.copy.RelativePath;

import java.util.List;

public class PatternMatcherFactoryTest {
    private PatternMatcher matcher;
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
