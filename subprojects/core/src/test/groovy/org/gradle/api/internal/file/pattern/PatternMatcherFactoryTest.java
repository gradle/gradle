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
import org.gradle.api.specs.Spec;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

public class PatternMatcherFactoryTest {
    private Spec<RelativePath> matcher;

    @Test public void testEmpty() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "");
        assertThat(matcher, matchesFile());
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "b")));
    }

    @Test public void testSlashDirection() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "a/b/c");
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "b")));
        assertThat(matcher, not(matchesFile("a", "b", "c", "d")));
        assertThat(matcher, not(matchesFile("a", "other", "c")));

        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "a\\b\\c");
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "b")));
        assertThat(matcher, not(matchesFile("a", "b", "c", "d")));
        assertThat(matcher, not(matchesFile("a", "other", "c")));
    }

    @Test public void testCaseSensitive() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "a/b/c");
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, not(matchesFile("a", "b", "C")));

        matcher = PatternMatcherFactory.getPatternMatcher(true, false, "a\\b\\c");
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, matchesFile("a", "b", "C"));
    }

    @Test public void testTrailingSlashIsReplacedWithTrailingGreedy() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "a/b/");
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "c")));
        assertThat(matcher, not(matchesFile("c", "b")));

        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "a\\b\\");
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "c")));
        assertThat(matcher, not(matchesFile("c", "b")));
    }

    @Test public void testGreedyWithTrailingName() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "**/*.jsp");
        assertThat(matcher, matchesFile("fred.jsp"));
        assertThat(matcher, matchesFile("a", "fred.jsp"));
        assertThat(matcher, matchesFile("a", "b", "fred.jsp"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("fred.txt")));
        assertThat(matcher, not(matchesFile("src", "fred.txt")));

        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "**/**/*.jsp");
        assertThat(matcher, matchesFile("fred.jsp"));
        assertThat(matcher, matchesFile("a", "fred.jsp"));
        assertThat(matcher, matchesFile("a", "b", "fred.jsp"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("fred.txt")));
        assertThat(matcher, not(matchesFile("src", "fred.txt")));
    }

    @Test public void testGreedyWithSingleNameFollowedByGreedy() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "**/*a*/**");
        assertThat(matcher, matchesFile("abc"));
        assertThat(matcher, matchesFile("a", "abc", "a"));
        assertThat(matcher, matchesFile("q", "abc", "r", "abc"));
        assertThat(matcher, matchesFile("q", "r", "abc"));
        assertThat(matcher, matchesFile("abc", "q", "r"));
        assertThat(matcher, matchesFile("q", "r", "abc", "q", "r"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("b")));
        assertThat(matcher, not(matchesFile("b", "b")));

        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "**/**/abc/**/**");
        assertThat(matcher, matchesFile("abc"));
        assertThat(matcher, matchesFile("a", "abc", "a"));
        assertThat(matcher, matchesFile("q", "abc", "r", "abc"));
        assertThat(matcher, matchesFile("q", "r", "abc"));
        assertThat(matcher, matchesFile("abc", "q", "r"));
        assertThat(matcher, matchesFile("q", "r", "abc", "q", "r"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("b")));
        assertThat(matcher, not(matchesFile("b", "b")));
    }

    @Test public void testWildcards() {
        matcher = PatternMatcherFactory.getPatternMatcher(false, true, "a/*");
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "b", "c")));
        assertThat(matcher, not(matchesFile("other", "b")));

        matcher = PatternMatcherFactory.getPatternMatcher(false, true, "?");
        assertThat(matcher, matchesFile("?"));
        assertThat(matcher, matchesFile("a"));
        assertThat(matcher, matchesFile("C"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("abc")));

        matcher = PatternMatcherFactory.getPatternMatcher(false, true, "?b??e*");
        assertThat(matcher, matchesFile("?b??e*"));
        assertThat(matcher, matchesFile("abcde"));
        assertThat(matcher, matchesFile("abcdefgh"));
        assertThat(matcher, not(matchesFile("aaaae")));
        assertThat(matcher, not(matchesFile("abcdfe")));
        assertThat(matcher, not(matchesFile("abc")));
    }

    @Test public void testLiteralsPartialMatchingDirs() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "a/b");
        assertThat(matcher, matchesDir());
        assertThat(matcher, matchesDir("a"));
        assertThat(matcher, matchesDir("a", "b"));
        assertThat(matcher, not(matchesDir("other")));
        assertThat(matcher, not(matchesDir("other", "b")));
        assertThat(matcher, not(matchesDir("b", "other")));
        assertThat(matcher, not(matchesDir("a", "b", "c")));
    }

    @Test public void testGreedy() {
        matcher = PatternMatcherFactory.getPatternMatcher(false, true, "**");
        assertThat(pathMatcher(matcher), instanceOf(AnythingMatcher.class));
        assertThat(matcher, matchesFile());
        assertThat(matcher, matchesFile("a"));
        assertThat(matcher, matchesFile("a", "b", "c"));

        matcher = PatternMatcherFactory.getPatternMatcher(false, true, "**/");
        assertThat(pathMatcher(matcher), instanceOf(AnythingMatcher.class));
        assertThat(matcher, matchesFile());
        assertThat(matcher, matchesFile("a"));
        assertThat(matcher, matchesFile("a", "b", "c"));

        matcher = PatternMatcherFactory.getPatternMatcher(false, true, "**/**/**");
        assertThat(pathMatcher(matcher), instanceOf(AnythingMatcher.class));
        assertThat(matcher, matchesFile());
        assertThat(matcher, matchesFile("a"));
        assertThat(matcher, matchesFile("a", "b", "c"));
    }

    @Test public void testGreedyPatternsMatchingFiles() {
        matcher = PatternMatcherFactory.getPatternMatcher(false, true, "**");
        assertThat(matcher, matchesFile());
        assertThat(matcher, matchesFile("a"));
        assertThat(matcher, matchesFile("a", "b", "c"));

        matcher = PatternMatcherFactory.getPatternMatcher(false, true, "**/a");
        assertThat(matcher, matchesFile("a"));
        assertThat(matcher, matchesFile("b", "a"));
        assertThat(matcher, matchesFile("a", "b", "a"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("b")));
        assertThat(matcher, not(matchesFile("a", "b")));
        assertThat(matcher, not(matchesFile("b", "a", "c")));

        matcher = PatternMatcherFactory.getPatternMatcher(false, true, "**/a/b/**");
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, matchesFile("c", "a", "b"));
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, matchesFile("c", "a", "b", "d"));
        assertThat(matcher, matchesFile("a", "b", "a", "b"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("b")));
        assertThat(matcher, not(matchesFile("a", "c", "b")));
        assertThat(matcher, not(matchesFile("c", "d")));

        matcher = PatternMatcherFactory.getPatternMatcher(false, true, "**/a/**/b");
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, matchesFile("a", "c", "b"));
        assertThat(matcher, matchesFile("c", "a", "b"));
        assertThat(matcher, matchesFile("c", "a", "d", "b"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "b", "c")));
        assertThat(matcher, not(matchesFile("c", "d")));

        matcher = PatternMatcherFactory.getPatternMatcher(false, true, "a/b/**");
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "c", "b")));
        assertThat(matcher, not(matchesFile("c", "a", "b")));
        assertThat(matcher, not(matchesFile("c", "d")));

        matcher = PatternMatcherFactory.getPatternMatcher(false, true, "a/b/**/c");
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, matchesFile("a", "b", "d", "c"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "b")));
        assertThat(matcher, not(matchesFile("a", "b", "c", "d")));
        assertThat(matcher, not(matchesFile("a", "c", "b", "c")));
        assertThat(matcher, not(matchesFile("d", "a", "b")));

        matcher = PatternMatcherFactory.getPatternMatcher(false, true, "a/b/**/c/**");
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, matchesFile("a", "b", "d", "c"));
        assertThat(matcher, matchesFile("a", "b", "c", "d"));
        assertThat(matcher, matchesFile("a", "b", "d", "c", "d"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "b")));
        assertThat(matcher, not(matchesFile("d", "a", "b")));

        matcher = PatternMatcherFactory.getPatternMatcher(false, true, "**/*");
        assertThat(matcher, matchesFile("a"));
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, not(matchesFile()));

        matcher = PatternMatcherFactory.getPatternMatcher(false, true, "*/**");
        assertThat(matcher, matchesFile("a"));
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, not(matchesFile()));

        matcher = PatternMatcherFactory.getPatternMatcher(false, true, "a/**/*");
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, matchesFile("a", "b", "c", "d"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("b", "a")));
    }

    @Test public void testGreedyPatternsPartialMatchingDirs() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "**");
        assertThat(matcher, matchesDir());
        assertThat(matcher, matchesDir("a"));
        assertThat(matcher, matchesDir("a", "b", "c"));

        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "**/a");
        assertThat(matcher, matchesDir());
        assertThat(matcher, matchesDir("a"));
        assertThat(matcher, matchesDir("b", "a"));
        assertThat(matcher, matchesDir("a", "b", "a"));
        assertThat(matcher, matchesDir("d"));

        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "**/a/b/**");
        assertThat(matcher, matchesDir());
        assertThat(matcher, matchesDir("a", "b"));
        assertThat(matcher, matchesDir("c", "a", "b"));
        assertThat(matcher, matchesDir("a", "b", "c"));
        assertThat(matcher, matchesDir("c", "a", "b", "d"));
        assertThat(matcher, matchesDir("a", "b", "a", "b"));
        assertThat(matcher, matchesDir("a"));
        assertThat(matcher, matchesDir("c"));
        assertThat(matcher, matchesDir("c", "a"));
        assertThat(matcher, matchesDir("c", "a", "a", "b"));

        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "a/b/**");
        assertThat(matcher, matchesDir());
        assertThat(matcher, matchesDir("a", "b"));
        assertThat(matcher, matchesDir("a", "b", "c"));
        assertThat(matcher, matchesDir("a"));
        assertThat(matcher, not(matchesDir("b")));
        assertThat(matcher, not(matchesDir("d")));
        assertThat(matcher, not(matchesDir("a", "c", "b")));
        assertThat(matcher, not(matchesDir("c", "a", "b")));

        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "a/b/**/c");
        assertThat(matcher, matchesDir());
        assertThat(matcher, matchesDir("a", "b", "c"));
        assertThat(matcher, matchesDir("a", "b", "d", "c"));
        assertThat(matcher, matchesDir("a"));
        assertThat(matcher, matchesDir("a", "b"));
        assertThat(matcher, matchesDir("a", "b", "d"));
        assertThat(matcher, matchesDir("a", "b", "c", "d"));
        assertThat(matcher, not(matchesDir("b")));
        assertThat(matcher, not(matchesDir("d")));
        assertThat(matcher, not(matchesDir("a", "c", "b", "c")));
        assertThat(matcher, not(matchesDir("d", "a", "b")));

        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "**/*");
        assertThat(matcher, matchesDir());
        assertThat(matcher, matchesDir("a"));
        assertThat(matcher, matchesDir("a", "b"));
        assertThat(matcher, matchesDir("a", "b", "c"));
        assertThat(matcher, matchesDir("a", "b", "d", "c"));

        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "*/**");
        assertThat(matcher, matchesDir());
        assertThat(matcher, matchesDir("a"));
        assertThat(matcher, matchesDir("a", "b"));
        assertThat(matcher, matchesDir("a", "b", "c"));
        assertThat(matcher, matchesDir("a", "b", "d", "c"));

        matcher = PatternMatcherFactory.getPatternMatcher(true, true, "a/**/*");
        assertThat(matcher, matchesDir());
        assertThat(matcher, matchesDir("a"));
        assertThat(matcher, matchesDir("a", "b"));
        assertThat(matcher, matchesDir("a", "b", "c"));
        assertThat(matcher, matchesDir("a", "b", "d", "c"));
        assertThat(matcher, not(matchesDir("b")));
        assertThat(matcher, not(matchesDir("b", "a")));
    }

    private PathMatcher pathMatcher(Spec<RelativePath> matcher) {
        return ((PatternMatcherFactory.PathMatcherBackedSpec) matcher).getPathMatcher();
    }

    private Matcher<Spec<RelativePath>> matchesFile(String... paths) {
        return matches(new RelativePath(true, paths));
    }

    private Matcher<Spec<RelativePath>> matchesDir(String... paths) {
        return matches(new RelativePath(false, paths));
    }

    private Matcher<Spec<RelativePath>> matches(final RelativePath path) {
        return new BaseMatcher<Spec<RelativePath>>() {
            public void describeTo(Description description) {
                description.appendText("matches ").appendValue(path);
            }

            public boolean matches(Object o) {
                Spec<RelativePath> matcher = (Spec<RelativePath>) o;
                return matcher.isSatisfiedBy(path);
            }
        };
    }
}
