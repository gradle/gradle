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

import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class PatternMatcherFactoryTest {
    private PatternMatcher matcher;

    @Test public void testNoStackOverflowForManyPatterns() {
        // The only reason for this unit test is to verify that no StackOverflowException is being thrown when
        // many patterns are passed to getPatternsMatcher. See https://github.com/gradle/gradle/issues/10329
        Set<String> manyPatterns =  new LinkedHashSet<String>();
        for (int i = 0; i < 5000; i++) {
            manyPatterns.add("some/package/Some" + i + "ClassName.class");
            manyPatterns.add("some/package/Some" + i + "ClassName.java");
            manyPatterns.add("some/package/Some" + i + "ClassName.h");
            manyPatterns.add("some/package/Some" + i + "ClassName$*.class");
            manyPatterns.add("some/package/Some" + i + "ClassName$*.java");
            manyPatterns.add("some/package/Some" + i + "ClassName$*.h");
        }
        matcher = PatternMatcherFactory.getPatternsMatcher(true, fixedSupplier(true), manyPatterns);
        assertThat(matcher, not(matchesFile("some", "package", "SomeClassName.java")));
        assertThat(matcher, matchesFile("some", "package", "Some123ClassName.java"));
    }

    @Test public void testManyOr() {
        Set<String> manyPatterns =  new LinkedHashSet<String>();
        manyPatterns.add("some/package/SomeClassName.class");
        manyPatterns.add("some/package/SomeClassName.java");
        manyPatterns.add("some/package/SomeClassName.h");
        manyPatterns.add("some/package/SomeClassName$*.class");
        manyPatterns.add("some/package/SomeClassName$*.java");
        manyPatterns.add("some/package/SomeClassName$*.h");
        matcher = PatternMatcherFactory.getPatternsMatcher(true, fixedSupplier(true), manyPatterns);
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, matchesFile("some", "package", "SomeClassName.java"));
        assertThat(matcher, matchesFile("some", "package", "SomeClassName.class"));
        assertThat(matcher, matchesFile("some", "package", "SomeClassName.h"));
        assertThat(matcher, matchesFile("some", "package", "SomeClassName$*.java"));
        assertThat(matcher, matchesFile("some", "package", "SomeClassName$*.class"));
        assertThat(matcher, matchesFile("some", "package", "SomeClassName$*.h"));
        assertThat(matcher, not(matchesFile("a")));
    }

    @Test public void testEmpty() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "");
        assertThat(matcher, matchesFile());
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "b")));
    }

    @Test public void testSlashDirection() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "a/b/c");
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "b")));
        assertThat(matcher, not(matchesFile("a", "b", "c", "d")));
        assertThat(matcher, not(matchesFile("a", "other", "c")));

        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "a\\b\\c");
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "b")));
        assertThat(matcher, not(matchesFile("a", "b", "c", "d")));
        assertThat(matcher, not(matchesFile("a", "other", "c")));
    }

    @Test public void testCaseSensitive() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "a/b/c");
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, not(matchesFile("a", "b", "C")));

        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(false), "a\\b\\c");
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, matchesFile("a", "b", "C"));
    }

    @Test public void testTrailingSlashIsReplacedWithTrailingGreedy() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "a/b/");
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "c")));
        assertThat(matcher, not(matchesFile("c", "b")));

        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "a\\b\\");
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "c")));
        assertThat(matcher, not(matchesFile("c", "b")));
    }

    @Test public void testDuplicateSeparatorIsIgnored() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "a//b");
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, not(matchesFile("a", "b", "c")));
        assertThat(matcher, not(matchesFile("a", "c")));
        assertThat(matcher, not(matchesFile("c", "b")));

        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "a\\\\b");
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, not(matchesFile("a", "b", "c")));
        assertThat(matcher, not(matchesFile("a", "c")));
        assertThat(matcher, not(matchesFile("c", "b")));
    }

    @Test public void testGreedyWithTrailingName() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "**/*.jsp");
        assertThat(matcher, matchesFile("fred.jsp"));
        assertThat(matcher, matchesFile("a", "fred.jsp"));
        assertThat(matcher, matchesFile("a", "b", "fred.jsp"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("fred.txt")));
        assertThat(matcher, not(matchesFile("src", "fred.txt")));

        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "**/**/*.jsp");
        assertThat(matcher, matchesFile("fred.jsp"));
        assertThat(matcher, matchesFile("a", "fred.jsp"));
        assertThat(matcher, matchesFile("a", "b", "fred.jsp"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("fred.txt")));
        assertThat(matcher, not(matchesFile("src", "fred.txt")));
    }

    @Test public void testGreedyWithSingleNameFollowedByGreedy() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "**/*a*/**");
        assertThat(matcher, matchesFile("abc"));
        assertThat(matcher, matchesFile("a", "abc", "a"));
        assertThat(matcher, matchesFile("q", "abc", "r", "abc"));
        assertThat(matcher, matchesFile("q", "r", "abc"));
        assertThat(matcher, matchesFile("abc", "q", "r"));
        assertThat(matcher, matchesFile("q", "r", "abc", "q", "r"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("b")));
        assertThat(matcher, not(matchesFile("b", "b")));

        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "**/**/abc/**/**");
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
        matcher = PatternMatcherFactory.getPatternMatcher(false, fixedSupplier(true), "a/*");
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "b", "c")));
        assertThat(matcher, not(matchesFile("other", "b")));

        matcher = PatternMatcherFactory.getPatternMatcher(false, fixedSupplier(true), "?");
        assertThat(matcher, matchesFile("?"));
        assertThat(matcher, matchesFile("a"));
        assertThat(matcher, matchesFile("C"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("abc")));

        matcher = PatternMatcherFactory.getPatternMatcher(false, fixedSupplier(true), "?b??e*");
        assertThat(matcher, matchesFile("?b??e*"));
        assertThat(matcher, matchesFile("abcde"));
        assertThat(matcher, matchesFile("abcdefgh"));
        assertThat(matcher, not(matchesFile("aaaae")));
        assertThat(matcher, not(matchesFile("abcdfe")));
        assertThat(matcher, not(matchesFile("abc")));
    }

    @Test public void testLiteralsPartialMatchingDirs() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "a/b");
        assertThat(matcher, matchesDir());
        assertThat(matcher, matchesDir("a"));
        assertThat(matcher, matchesDir("a", "b"));
        assertThat(matcher, not(matchesDir("other")));
        assertThat(matcher, not(matchesDir("other", "b")));
        assertThat(matcher, not(matchesDir("b", "other")));
        assertThat(matcher, not(matchesDir("a", "b", "c")));
    }

    @Test public void testGreedy() {
        matcher = PatternMatcherFactory.getPatternMatcher(false, fixedSupplier(true), "**");
        assertThat(pathMatcher(matcher), instanceOf(AnythingMatcher.class));
        assertThat(matcher, matchesFile());
        assertThat(matcher, matchesFile("a"));
        assertThat(matcher, matchesFile("a", "b", "c"));

        matcher = PatternMatcherFactory.getPatternMatcher(false, fixedSupplier(true), "**/");
        assertThat(pathMatcher(matcher), instanceOf(AnythingMatcher.class));
        assertThat(matcher, matchesFile());
        assertThat(matcher, matchesFile("a"));
        assertThat(matcher, matchesFile("a", "b", "c"));

        matcher = PatternMatcherFactory.getPatternMatcher(false, fixedSupplier(true), "**/**/**");
        assertThat(pathMatcher(matcher), instanceOf(AnythingMatcher.class));
        assertThat(matcher, matchesFile());
        assertThat(matcher, matchesFile("a"));
        assertThat(matcher, matchesFile("a", "b", "c"));
    }

    @Test public void testGreedyPatternsMatchingFiles() {
        matcher = PatternMatcherFactory.getPatternMatcher(false, fixedSupplier(true), "**");
        assertThat(matcher, matchesFile());
        assertThat(matcher, matchesFile("a"));
        assertThat(matcher, matchesFile("a", "b", "c"));

        matcher = PatternMatcherFactory.getPatternMatcher(false, fixedSupplier(true), "**/a");
        assertThat(matcher, matchesFile("a"));
        assertThat(matcher, matchesFile("b", "a"));
        assertThat(matcher, matchesFile("a", "b", "a"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("b")));
        assertThat(matcher, not(matchesFile("a", "b")));
        assertThat(matcher, not(matchesFile("b", "a", "c")));

        matcher = PatternMatcherFactory.getPatternMatcher(false, fixedSupplier(true), "**/a/b/**");
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

        matcher = PatternMatcherFactory.getPatternMatcher(false, fixedSupplier(true), "**/a/**/b");
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, matchesFile("a", "c", "b"));
        assertThat(matcher, matchesFile("c", "a", "b"));
        assertThat(matcher, matchesFile("c", "a", "d", "b"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "b", "c")));
        assertThat(matcher, not(matchesFile("c", "d")));

        matcher = PatternMatcherFactory.getPatternMatcher(false, fixedSupplier(true), "a/b/**");
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "c", "b")));
        assertThat(matcher, not(matchesFile("c", "a", "b")));
        assertThat(matcher, not(matchesFile("c", "d")));

        matcher = PatternMatcherFactory.getPatternMatcher(false, fixedSupplier(true), "a/b/**/c");
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, matchesFile("a", "b", "d", "c"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "b")));
        assertThat(matcher, not(matchesFile("a", "b", "c", "d")));
        assertThat(matcher, not(matchesFile("a", "c", "b", "c")));
        assertThat(matcher, not(matchesFile("d", "a", "b")));

        matcher = PatternMatcherFactory.getPatternMatcher(false, fixedSupplier(true), "a/b/**/c/**");
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, matchesFile("a", "b", "d", "c"));
        assertThat(matcher, matchesFile("a", "b", "c", "d"));
        assertThat(matcher, matchesFile("a", "b", "d", "c", "d"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("a", "b")));
        assertThat(matcher, not(matchesFile("d", "a", "b")));

        matcher = PatternMatcherFactory.getPatternMatcher(false, fixedSupplier(true), "**/*");
        assertThat(matcher, matchesFile("a"));
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, not(matchesFile()));

        matcher = PatternMatcherFactory.getPatternMatcher(false, fixedSupplier(true), "*/**");
        assertThat(matcher, matchesFile("a"));
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, not(matchesFile()));

        matcher = PatternMatcherFactory.getPatternMatcher(false, fixedSupplier(true), "a/**/*");
        assertThat(matcher, matchesFile("a", "b"));
        assertThat(matcher, matchesFile("a", "b", "c"));
        assertThat(matcher, matchesFile("a", "b", "c", "d"));
        assertThat(matcher, not(matchesFile()));
        assertThat(matcher, not(matchesFile("a")));
        assertThat(matcher, not(matchesFile("b", "a")));
    }

    @Test public void testGreedyPatternsPartialMatchingDirs() {
        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "**");
        assertThat(matcher, matchesDir());
        assertThat(matcher, matchesDir("a"));
        assertThat(matcher, matchesDir("a", "b", "c"));

        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "**/a");
        assertThat(matcher, matchesDir());
        assertThat(matcher, matchesDir("a"));
        assertThat(matcher, matchesDir("b", "a"));
        assertThat(matcher, matchesDir("a", "b", "a"));
        assertThat(matcher, matchesDir("d"));

        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "**/a/b/**");
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

        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "a/b/**");
        assertThat(matcher, matchesDir());
        assertThat(matcher, matchesDir("a", "b"));
        assertThat(matcher, matchesDir("a", "b", "c"));
        assertThat(matcher, matchesDir("a"));
        assertThat(matcher, not(matchesDir("b")));
        assertThat(matcher, not(matchesDir("d")));
        assertThat(matcher, not(matchesDir("a", "c", "b")));
        assertThat(matcher, not(matchesDir("c", "a", "b")));

        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "a/b/**/c");
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

        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "**/*");
        assertThat(matcher, matchesDir());
        assertThat(matcher, matchesDir("a"));
        assertThat(matcher, matchesDir("a", "b"));
        assertThat(matcher, matchesDir("a", "b", "c"));
        assertThat(matcher, matchesDir("a", "b", "d", "c"));

        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "*/**");
        assertThat(matcher, matchesDir());
        assertThat(matcher, matchesDir("a"));
        assertThat(matcher, matchesDir("a", "b"));
        assertThat(matcher, matchesDir("a", "b", "c"));
        assertThat(matcher, matchesDir("a", "b", "d", "c"));

        matcher = PatternMatcherFactory.getPatternMatcher(true, fixedSupplier(true), "a/**/*");
        assertThat(matcher, matchesDir());
        assertThat(matcher, matchesDir("a"));
        assertThat(matcher, matchesDir("a", "b"));
        assertThat(matcher, matchesDir("a", "b", "c"));
        assertThat(matcher, matchesDir("a", "b", "d", "c"));
        assertThat(matcher, not(matchesDir("b")));
        assertThat(matcher, not(matchesDir("b", "a")));
    }

    private static PathMatcher pathMatcher(PatternMatcher matcher) {
        return ((PatternMatcherFactory.DefaultPatternMatcher) matcher).getPathMatcher();
    }

    private static Matcher<PatternMatcher> matchesFile(String... segments) {
        return matches(segments, true);
    }

    private static Matcher<PatternMatcher> matchesDir(String... segments) {
        return matches(segments, false);
    }

    private static Matcher<PatternMatcher> matches(final String[] segments, final boolean isFile) {
        return new BaseMatcher<PatternMatcher>() {
            public void describeTo(Description description) {
                description.appendText("matches ").appendValue(Joiner.on("/").join(segments));
            }

            public boolean matches(Object o) {
                PatternMatcher matcher = (PatternMatcher) o;
                return matcher.test(segments, isFile);
            }
        };
    }

    private static <T> Supplier<T> fixedSupplier(final T value) {
        return new Supplier<T>() {
            @Override
            public T get() {
                return value;
            }
        };
    }
}
