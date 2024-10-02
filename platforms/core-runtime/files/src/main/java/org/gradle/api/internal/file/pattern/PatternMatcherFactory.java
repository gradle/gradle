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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import java.util.List;

public class PatternMatcherFactory {

    private static final EndOfPathMatcher END_OF_PATH_MATCHER = new EndOfPathMatcher();
    private static final Splitter PATH_SPLITTER = Splitter.on(CharMatcher.anyOf("\\/")).omitEmptyStrings();

    public static PatternMatcher getPatternsMatcher(boolean partialMatchDirs, Supplier<Boolean> caseSensitive, Iterable<String> patterns) {
        PatternMatcher matcher = PatternMatcher.MATCH_ALL;
        for (String pattern : patterns) {
            PatternMatcher patternMatcher = getPatternMatcher(partialMatchDirs, caseSensitive, pattern);
            matcher = matcher == PatternMatcher.MATCH_ALL
                ? patternMatcher
                : matcher.or(patternMatcher);
        }
        return matcher;
    }

    public static PatternMatcher getPatternMatcher(final boolean partialMatchDirs, final Supplier<Boolean> caseSensitive, final String pattern) {
        Supplier<PathMatcher> pathMatcherSupplier = new Supplier<PathMatcher>() {
            @Override
            public PathMatcher get() {
                return compile(caseSensitive.get(), pattern);
            }
        };
        return new DefaultPatternMatcher(partialMatchDirs, Suppliers.memoize(pathMatcherSupplier));
    }

    public static PathMatcher compile(boolean caseSensitive, String pattern) {
        if (pattern.length() == 0) {
            return END_OF_PATH_MATCHER;
        }

        // trailing / or \ assumes **
        if (pattern.endsWith("/") || pattern.endsWith("\\")) {
            pattern = pattern + "**";
        }
        List<String> parts = PATH_SPLITTER.splitToList(pattern);
        return compile(parts, 0, caseSensitive);
    }

    private static PathMatcher compile(List<String> parts, int startIndex, boolean caseSensitive) {
        if (startIndex >= parts.size()) {
            return END_OF_PATH_MATCHER;
        }
        int pos = startIndex;
        while (pos < parts.size() && parts.get(pos).equals("**")) {
            pos++;
        }
        if (pos > startIndex) {
            if (pos == parts.size()) {
                return new AnythingMatcher();
            }
            return new GreedyPathMatcher(compile(parts, pos, caseSensitive));
        }
        return new FixedStepPathMatcher(PatternStepFactory.getStep(parts.get(pos), caseSensitive), compile(parts, pos + 1, caseSensitive));
    }

    @VisibleForTesting
    static class DefaultPatternMatcher extends PatternMatcher {
        private final boolean partialMatchDirs;
        private final Supplier<PathMatcher> pathMatcher;

        public DefaultPatternMatcher(boolean partialMatchDirs, Supplier<PathMatcher> pathMatcher) {
            this.partialMatchDirs = partialMatchDirs;
            this.pathMatcher = pathMatcher;
        }

        @VisibleForTesting
        PathMatcher getPathMatcher() {
            return pathMatcher.get();
        }

        @Override
        public boolean test(String[] segments, boolean file) {
            if (file || !partialMatchDirs) {
                return pathMatcher.get().matches(segments, 0);
            } else {
                return pathMatcher.get().isPrefix(segments, 0);
            }
        }
    }
}
