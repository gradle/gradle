/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.collect.Lists;

import java.util.List;

public abstract class PatternMatcher {
    public static final PatternMatcher MATCH_ALL = new PatternMatcher() {
        @Override
        public boolean test(String[] segments, boolean isFile) {
            return true;
        }

        @Override
        public PatternMatcher and(PatternMatcher other) {
            return other;
        }

        @Override
        public PatternMatcher or(PatternMatcher other) {
            return this;
        }
    };

    public abstract boolean test(String[] segments, boolean isFile);

    public PatternMatcher and(final PatternMatcher other) {
        return new And(PatternMatcher.this, other);
    }

    public PatternMatcher or(final PatternMatcher other) {
        return new Or(PatternMatcher.this, other);
    }

    public PatternMatcher negate() {
        return new PatternMatcher() {
            @Override
            public boolean test(String[] segments, boolean isFile) {
                return !PatternMatcher.this.test(segments, isFile);
            }
        };
    }

    private static final class Or extends PatternMatcher {
        private final List<PatternMatcher> parts = Lists.newLinkedList();

        public Or(PatternMatcher patternMatcher, PatternMatcher other) {
            parts.add(patternMatcher);
            parts.add(other);
        }

        @Override
        public PatternMatcher or(PatternMatcher other) {
            parts.add(other);
            return this;
        }

        @Override
        public boolean test(String[] segments, boolean isFile) {
            for (PatternMatcher part : parts) {
                if (part.test(segments, isFile)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class And extends PatternMatcher {
        private final List<PatternMatcher> parts = Lists.newLinkedList();

        public And(PatternMatcher patternMatcher, PatternMatcher other) {
            parts.add(patternMatcher);
            parts.add(other);
        }

        @Override
        public PatternMatcher and(PatternMatcher other) {
            parts.add(other);
            return this;
        }

        @Override
        public boolean test(String[] segments, boolean isFile) {
            for (PatternMatcher part : parts) {
                if (!part.test(segments, isFile)) {
                    return false;
                }
            }
            return true;
        }
    }
}
