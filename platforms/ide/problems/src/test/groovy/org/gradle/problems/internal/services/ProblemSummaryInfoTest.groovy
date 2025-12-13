/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.problems.internal.services


import spock.lang.Specification

class ProblemSummaryInfoTest extends Specification {
    def "construction"() {
        when:
        def info = new ProblemSummaryInfo()

        then:
        info != null
    }

    def "increase count"() {
        when:
        def info = new ProblemSummaryInfo()
        info.increaseCount()

        then:
        info.count == 1
    }

    def "add hash and return false if already seen"() {
        when:
        def info = new ProblemSummaryInfo()

        then:
        info.addHash(1)
        !info.addHash(1)
        info.addHash(2)
    }

    def "determines whether a problem should be emitted correctly"() {
        when:
        def info = new ProblemSummaryInfo()

        then:
        def threshold = 4
        info.shouldEmit(1, threshold) // allowed because it's the first occurrence and within the threshold
        !info.shouldEmit(1, threshold) // not allowed because it's the second occurrence
        !info.shouldEmit(1, threshold) // not allowed because it's the third occurrence
        info.shouldEmit(2, threshold) // allowed because it's the first occurrence of "2" and within the threshold
        info.shouldEmit(3, threshold) // allowed because it's the first occurrence of "3" and within the threshold
        info.shouldEmit(threshold, threshold) // allowed because it's the first occurrence of "4" and within the threshold
        !info.shouldEmit(5, threshold) // not allowed because it's the first occurrence of "5" and outside the threshold
    }
}
