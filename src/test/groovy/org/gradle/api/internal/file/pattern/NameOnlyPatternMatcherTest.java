package org.gradle.api.internal.file.pattern;

import org.junit.Test;
import static org.junit.Assert.*;
import org.gradle.api.internal.file.RelativePath;
import org.gradle.api.specs.Spec;

public class NameOnlyPatternMatcherTest {

    @Test public void testLiteralName() {
        Spec<RelativePath> matcher;
        RelativePath path;

        matcher = new NameOnlyPatternMatcher(true, true, "fred.txt");

        path = new RelativePath(true, "fred.txt");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "something.else");
        assertFalse(matcher.isSatisfiedBy(path));
    }

    @Test public void testPartialMatch() {
        Spec<RelativePath> matcher;
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
        Spec<RelativePath> matcher;
        RelativePath path;

        matcher = new NameOnlyPatternMatcher(true, true, "*.jsp");
        path = new RelativePath(true, "fred.jsp");
        assertTrue(matcher.isSatisfiedBy(path));

        path = new RelativePath(true, "fred.java");
        assertFalse(matcher.isSatisfiedBy(path));
    }
}
