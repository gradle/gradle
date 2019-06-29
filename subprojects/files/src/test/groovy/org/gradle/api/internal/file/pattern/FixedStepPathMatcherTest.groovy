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

package org.gradle.api.internal.file.pattern

import spock.lang.Specification

class FixedStepPathMatcherTest extends Specification {
    def "calculates min and max number of segments"() {
        def next = Stub(PathMatcher) {
            getMinSegments() >> 2
            getMaxSegments() >> 3
        }
        def matcher = new FixedStepPathMatcher(Stub(PatternStep), next)

        expect:
        matcher.minSegments == 3
        matcher.maxSegments == 4
    }

    def "calculates min and max number of segments when next pattern is unbounded"() {
        def next = Stub(PathMatcher) {
            getMinSegments() >> 2
            getMaxSegments() >> Integer.MAX_VALUE
        }
        def matcher = new FixedStepPathMatcher(Stub(PatternStep), next)

        expect:
        matcher.minSegments == 3
        matcher.maxSegments == Integer.MAX_VALUE
    }

    def "matches path that is appropriate length and matches the step and the next pattern"() {
        def matcher = new FixedStepPathMatcher(step("a"), matchesLastOrSecondLast("c"))

        expect:
        matcher.matches(["a", "c"] as String[], 0)
        matcher.matches(["a", "c", "d"] as String[], 0)
        matcher.matches(["prefix", "a", "c"] as String[], 1)

        and:
        !matcher.matches([] as String[], 0)
        !matcher.matches(["a"] as String[], 0)
        !matcher.matches(["other"] as String[], 0)
        !matcher.matches(["a", "other"] as String[], 0)
        !matcher.matches(["a", "other", "c"] as String[], 0)
        !matcher.matches(["a", "c"] as String[], 1)
        !matcher.matches(["prefix", "a"] as String[], 1)
        !matcher.matches(["prefix", "a", "other"] as String[], 1)
    }

    def "path is a prefix when it matches the step and has zero or one element"() {
        def matcher = new FixedStepPathMatcher(step("a"), matchesLastOrSecondLast("c"))

        expect:
        matcher.isPrefix([] as String[], 0)
        matcher.isPrefix(["a"] as String[], 0)
        matcher.isPrefix(["prefix", "a"] as String[], 1)
        !matcher.isPrefix(["other"] as String[], 0)
        !matcher.isPrefix(["prefix", "other"] as String[], 1)
    }

    def "path is a prefix when it matches the step and is a prefix of next pattern"() {
        def matcher = new FixedStepPathMatcher(step("a"), matchesLastOrSecondLast("c"))

        expect:
        matcher.isPrefix(["a", "c"] as String[], 0)
        matcher.isPrefix(["a", "c", "d"] as String[], 0)
        matcher.isPrefix(["prefix", "a", "c", "d"] as String[], 1)
        !matcher.isPrefix(["other", "c"] as String[], 0)
        !matcher.isPrefix(["prefix", "other", "c"] as String[], 1)
        !matcher.isPrefix(["a", "other"] as String[], 0)
    }

    def matchesLastOrSecondLast(String value) {
        return Stub(PathMatcher) {
            getMinSegments() >> 1
            getMaxSegments() >> 2
            matches(_, _) >> { String[] segments, int index ->
                return segments[index] == value && segments.length - index <= 2
            }
            isPrefix(_, _) >> { String[] segments, int index ->
                return segments[index] == value;
            }
        }
    }

    def step(String value) {
        return Stub(PatternStep) {
            matches(value) >> true
        }
    }
}
