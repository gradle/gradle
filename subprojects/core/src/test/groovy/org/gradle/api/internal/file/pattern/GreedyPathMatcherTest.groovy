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

class GreedyPathMatcherTest extends Specification {
    def "calculates min and max number of segments"() {
        def next = Stub(PathMatcher) {
            getMinSegments() >> 2
            getMaxSegments() >> 3
        }
        def matcher = new GreedyPathMatcher(next)

        expect:
        matcher.minSegments == 2
        matcher.maxSegments == Integer.MAX_VALUE
    }

    def "matches a path whose suffix matches the next pattern"() {
        def matcher = new GreedyPathMatcher(matchesLastOrSecondLast("c"))

        expect:
        !matcher.matches(["a"] as String[], 0)
        !matcher.matches(["a", "c", "d", "b"] as String[], 0)

        matcher.matches(["c"] as String[], 0)
        matcher.matches(["prefix", "c"] as String[], 1)
        matcher.matches(["a", "c"] as String[], 0)
        matcher.matches(["a", "c", "d"] as String[], 0)
        matcher.matches(["a", "b", "c", "d"] as String[], 0)
        matcher.matches(["a", "b", "c", "d"] as String[], 1)
    }

    def "every path is a prefix"() {
        def matcher = new GreedyPathMatcher(matchesLastOrSecondLast("c"))

        expect:
        matcher.isPrefix(["a"] as String[], 0)
        matcher.isPrefix(["a", "b"] as String[], 1)
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
}
