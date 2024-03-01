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

public class FixedStepPathMatcher implements PathMatcher {
    private final PatternStep step;
    private final PathMatcher next;
    private final int minSegments;
    private final int maxSegments;

    public FixedStepPathMatcher(PatternStep step, PathMatcher next) {
        this.step = step;
        this.next = next;
        minSegments = 1 + next.getMinSegments();
        maxSegments = next.getMaxSegments() == Integer.MAX_VALUE ? Integer.MAX_VALUE : next.getMaxSegments() + 1;
    }

    @Override
    public String toString() {
        return "{fixed-step: " + step + ", next: " + next + "}";
    }

    @Override
    public int getMinSegments() {
        return minSegments;
    }

    @Override
    public int getMaxSegments() {
        return maxSegments;
    }

    @Override
    public boolean matches(String[] segments, int startIndex) {
        int remaining = segments.length - startIndex;
        if (remaining < minSegments || remaining > maxSegments) {
            return false;
        }
        if (!step.matches(segments[startIndex])) {
            return false;
        }
        return next.matches(segments, startIndex + 1);
    }

    @Override
    public boolean isPrefix(String[] segments, int startIndex) {
        if (startIndex == segments.length) {
            // Empty path, might match when more elements added
            return true;
        }
        if (!step.matches(segments[startIndex])) {
            // Does not match element, will never match when more elements added
            return false;
        }
        if (startIndex +1 == segments.length) {
            // End of path, might match when more elements added
            return true;
        }
        return next.isPrefix(segments, startIndex + 1);
    }
}
