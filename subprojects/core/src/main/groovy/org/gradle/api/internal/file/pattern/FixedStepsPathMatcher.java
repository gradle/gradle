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

import java.util.List;

public class FixedStepsPathMatcher implements PathMatcher {
    private final List<PatternStep> steps;
    private final PathMatcher next;
    private final int minSegments;
    private final int maxSegments;

    public FixedStepsPathMatcher(List<PatternStep> steps, PathMatcher next) {
        this.steps = steps;
        this.next = next;
        minSegments = steps.size() + next.getMinSegments();
        maxSegments = next.getMaxSegments() == Integer.MAX_VALUE ? Integer.MAX_VALUE : next.getMaxSegments() + steps.size();
    }

    public int getMinSegments() {
        return minSegments;
    }

    public int getMaxSegments() {
        return maxSegments;
    }

    public boolean matches(String[] segments, int startIndex) {
        int remaining = segments.length - startIndex;
        if (remaining < minSegments || remaining > maxSegments) {
            return false;
        }
        int pos = startIndex;
        for (int i = 0; i < steps.size(); i++, pos++) {
            PatternStep step = steps.get(i);
            if (!step.matches(segments[pos])) {
                return false;
            }
        }
        return next.matches(segments, pos);
    }

    public boolean isPrefix(String[] segments, int startIndex) {
        int pos = startIndex;
        for (int i = 0; pos < segments.length && i < steps.size(); i++, pos++) {
            PatternStep step = steps.get(i);
            if (!step.matches(segments[pos])) {
                return false;
            }
        }
        if (pos == segments.length) {
            return true;
        }
        return next.isPrefix(segments, pos);
    }
}
