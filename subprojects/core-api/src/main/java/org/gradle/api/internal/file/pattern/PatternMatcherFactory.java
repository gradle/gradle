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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;

public class PatternMatcherFactory {

    private static final EndOfPathMatcher END_OF_PATH_MATCHER = new EndOfPathMatcher();
    private static final String PATH_SEPARATORS = "\\/";

    public static Spec<RelativePath> getPatternMatcher(boolean partialMatchDirs, boolean caseSensitive, String pattern) {
        PathMatcher pathMatcher = compile(caseSensitive, pattern);
        return new PathMatcherBackedSpec(partialMatchDirs, pathMatcher);
    }

    public static PathMatcher compile(boolean caseSensitive, String pattern) {
        if (pattern.length() == 0) {
            return END_OF_PATH_MATCHER;
        }

        // trailing / or \ assumes **
        if (pattern.endsWith("/") || pattern.endsWith("\\")) {
            pattern = pattern + "**";
        }
        String[] parts = StringUtils.split(pattern, PATH_SEPARATORS);
        return compile(parts, 0, caseSensitive);
    }

    private static PathMatcher compile(String[] parts, int startIndex, boolean caseSensitive) {
        if (startIndex >= parts.length) {
            return END_OF_PATH_MATCHER;
        }
        int pos = startIndex;
        while (pos < parts.length && parts[pos].equals("**")) {
            pos++;
        }
        if (pos > startIndex) {
            if (pos == parts.length) {
                return new AnythingMatcher();
            }
            return new GreedyPathMatcher(compile(parts, pos, caseSensitive));
        }
        return new FixedStepPathMatcher(PatternStepFactory.getStep(parts[pos], caseSensitive), compile(parts, pos + 1, caseSensitive));
    }

    static class PathMatcherBackedSpec implements Spec<RelativePath> {
        private final boolean partialMatchDirs;
        private final PathMatcher pathMatcher;

        PathMatcherBackedSpec(boolean partialMatchDirs, PathMatcher pathMatcher) {
            this.partialMatchDirs = partialMatchDirs;
            this.pathMatcher = pathMatcher;
        }

        PathMatcher getPathMatcher() {
            return pathMatcher;
        }

        @Override
        public boolean isSatisfiedBy(RelativePath element) {
            if (element.isFile() || !partialMatchDirs) {
                return pathMatcher.matches(element.getSegments(), 0);
            } else {
                return pathMatcher.isPrefix(element.getSegments(), 0);
            }
        }
    }
}
