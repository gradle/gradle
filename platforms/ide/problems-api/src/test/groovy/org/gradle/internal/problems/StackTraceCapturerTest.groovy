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

import com.google.common.base.Supplier
import spock.lang.Specification

class StackTraceCapturerTest extends Specification {

    def boundedCapturer = Mock(BoundedCallerStackCapturer)
    def fullException = new Exception()
    def fullFactory = { fullException } as Supplier

    def "spends the full budget, then the bounded budget, then captures nothing"() {
        given:
        def boundedException = new Exception()
        def capturer = new StackTraceCapturer(2, 3, boundedCapturer)

        when:
        def results = (1..6).collect { capturer.captureStack(fullFactory, true) }

        then:
        results[0].is(fullException)
        results[1].is(fullException)
        results[2].is(boundedException)
        results[3].is(boundedException)
        results[4].is(boundedException)
        results[5] == null

        3 * boundedCapturer.captureCallerStack() >> boundedException
    }

    def "without bounded fallback, captures full then nothing and leaves the bounded budget untouched"() {
        given:
        def capturer = new StackTraceCapturer(2, 3, boundedCapturer)

        when:
        def results = (1..4).collect { capturer.captureStack(fullFactory, false) }

        then:
        results[0].is(fullException)
        results[1].is(fullException)
        results[2] == null
        results[3] == null

        0 * boundedCapturer.captureCallerStack()
    }

    def "an unbounded bounded budget keeps capturing past the full cap"() {
        given:
        def boundedException = new Exception()
        def capturer = new StackTraceCapturer(1, Integer.MAX_VALUE, boundedCapturer)

        when:
        def results = (1..100).collect { capturer.captureStack(fullFactory, true) }

        then:
        results[0].is(fullException)
        results[1..99].every { it.is(boundedException) }

        99 * boundedCapturer.captureCallerStack() >> boundedException
    }

    def "a null bounded capture still spends the bounded budget"() {
        given:
        def capturer = new StackTraceCapturer(0, 2, boundedCapturer)

        when:
        def results = (1..3).collect { capturer.captureStack(fullFactory, true) }

        then:
        results.every { it == null }

        2 * boundedCapturer.captureCallerStack() >> null
    }
}
