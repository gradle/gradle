package org.gradle.api.internal.tasks.copy.pattern;

import org.junit.Test;
import static org.junit.Assert.*;
import org.gradle.api.internal.tasks.copy.RelativePath;

public class NameOnlyPatternMatcherTest {

    @Test public void testLiteralName() {
        PatternMatcher matcher;
        RelativePath path;

        matcher = new NameOnlyPatternMatcher(true, true, "fred.txt");

        path = new RelativePath(true, "fred.txt");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "something.else");
        assertFalse(matcher.isSatisfiedBy(path));
    }

    @Test public void testPartialMatch() {
        PatternMatcher matcher;
        RelativePath path;

        matcher = new NameOnlyPatternMatcher(true, true, "fred");
        path = new RelativePath(true, "fred");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(false, "subdir");
        assertTrue(matcher.isSatisfiedBy(path));


        matcher = new NameOnlyPatternMatcher(false, true, "fred");
        path = new RelativePath(true, "fred");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(false, "subdir");
        assertFalse(matcher.isSatisfiedBy(path));
    }

    @Test public void testWildcardInName() {
        PatternMatcher matcher;
        RelativePath path;

        matcher = new NameOnlyPatternMatcher(true, true, "*.jsp");
        path = new RelativePath(true, "fred.jsp");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "fred.java");
        assertFalse(matcher.isSatisfiedBy(path));
    }
}
