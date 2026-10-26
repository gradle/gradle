/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.problems

import org.gradle.problems.buildtree.ProblemStream
import spock.lang.Specification

class StackTraceCapturerTest extends Specification {

    def boundedCapturer = Mock(BoundedCallerStackCapturer)

    def "captureCaller spends the full budget, then the bounded budget, then captures nothing"() {
        given:
        def boundedException = new Exception()
        def capturer = new StackTraceCapturer(2, 3, boundedCapturer)

        when:
        def results = (1..6).collect { capturer.captureLocation() }

        then:
        results[0] != null
        results[1] != null
        !results[0].is(boundedException)
        !results[1].is(boundedException)
        results[2].is(boundedException)
        results[3].is(boundedException)
        results[4].is(boundedException)
        results[5] == null

        3 * boundedCapturer.captureCallerStack() >> boundedException
    }

    def "captureRetainableException runs the factory only while the full budget lasts"() {
        given:
        def supplied = new Exception()
        def creator = { supplied } as ProblemStream.ExceptionCreator
        def capturer = new StackTraceCapturer(2, 1, boundedCapturer)

        when:
        def results = (1..3).collect { capturer.captureRetainableException(creator) }

        then:
        results[0].is(supplied)
        results[1].is(supplied)
        results[2] == null

        0 * boundedCapturer.captureCallerStack()
    }

    def "captureLocation still locates a problem the budget could not afford an exception for"() {
        given:
        def boundedException = new Exception()
        def creator = { new Exception() } as ProblemStream.ExceptionCreator
        def capturer = new StackTraceCapturer(1, 1, boundedCapturer)
        capturer.captureRetainableException(creator)

        when:
        def unaffordable = capturer.captureRetainableException(creator)
        def located = capturer.captureLocation()
        def pastBoundedBudget = capturer.captureLocation()

        then:
        unaffordable == null
        located.is(boundedException)
        pastBoundedBudget == null

        1 * boundedCapturer.captureCallerStack() >> boundedException
    }

    def "captureCaller with an unbounded bounded budget keeps capturing past the full cap"() {
        given:
        def boundedException = new Exception()
        def capturer = new StackTraceCapturer(1, Integer.MAX_VALUE, boundedCapturer)

        when:
        def results = (1..100).collect { capturer.captureLocation() }

        then:
        results[0] != null
        !results[0].is(boundedException)
        results[1..99].every { it.is(boundedException) }

        99 * boundedCapturer.captureCallerStack() >> boundedException
    }

    def "captureCaller spends the bounded budget even when the bounded capture is null"() {
        given:
        def capturer = new StackTraceCapturer(0, 2, boundedCapturer)

        when:
        def results = (1..3).collect { capturer.captureLocation() }

        then:
        results.every { it == null }

        2 * boundedCapturer.captureCallerStack() >> null
    }
}
