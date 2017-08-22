/*
 * Copyright 2014 the original author or authors.
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

public class GreedyPathMatcher implements PathMatcher {
    private final PathMatcher next;

    public GreedyPathMatcher(PathMatcher next) {
        this.next = next;
    }

    @Override
    public String toString() {
        return "{greedy next: " + next + "}";
    }

    public int getMaxSegments() {
        return Integer.MAX_VALUE;
    }

    public int getMinSegments() {
        return next.getMinSegments();
    }

    public boolean matches(String[] segments, int startIndex) {
        int pos = segments.length - next.getMinSegments();
        int minPos = Math.max(startIndex, segments.length - next.getMaxSegments());
        for (; pos >= minPos; pos--) {
            if (next.matches(segments, pos)) {
                return true;
            }
        }
        return false;
    }

    public boolean isPrefix(String[] segments, int startIndex) {
        return true;
    }
}
