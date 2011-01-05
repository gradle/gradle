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
