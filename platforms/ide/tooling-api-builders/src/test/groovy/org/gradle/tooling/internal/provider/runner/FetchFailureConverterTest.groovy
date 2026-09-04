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

package org.gradle.tooling.internal.provider.runner

import org.gradle.internal.problems.failure.DefaultFailureFactory
import org.gradle.internal.problems.failure.Failure
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class FetchFailureConverterTest extends Specification {

    def converter = new FetchFailureConverter()

    def "converts without stack frames, keeping the messages and the cause chain"() {
        def throwable = new RuntimeException("top", new IllegalStateException("bottom"))

        when:
        def converted = converter.convertWithoutStackTrace(failureOf(throwable))

        then:
        converted.message == "top"
        converted.causes[0].message == "bottom"

        and:
        def description = converted.description
        description.contains("java.lang.RuntimeException: top")
        description.contains("Caused by: java.lang.IllegalStateException: bottom")
        !containsRenderedStackTrace(description)
    }

    def "converts a throwable without stack frames and interns it like a failure"() {
        def throwable = new RuntimeException("boom")

        when:
        def fromThrowable = converter.convertWithoutStackTrace(throwable)
        def fromFailure = converter.convertWithoutStackTrace(failureOf(throwable))

        then: "the throwable and the failure wrapping it convert to the same instance"
        fromThrowable.is(fromFailure)

        and:
        !containsRenderedStackTrace(fromThrowable.description)
    }

    def "reuses the converted failure when two failures share the same original throwable"() {
        def throwable = new RuntimeException("boom")

        when:
        def first = converter.convertWithoutStackTrace(failureOf(throwable))
        def second = converter.convertWithoutStackTrace(failureOf(throwable))

        then:
        first.is(second)
    }

    def "consults the identity cache before converting a throwable to a failure"() {
        def throwable = new CauseAccessGuardException("boom")
        def first = converter.convertWithoutStackTrace(failureOf(throwable))
        throwable.causeAccessAllowed = false

        when:
        def second = converter.convertWithoutStackTrace(throwable)

        then: "the factory is bypassed, so it never tries to inspect the throwable again"
        second.is(first)
    }

    def "converts failures with distinct originals into distinct instances"() {
        when:
        def first = converter.convertWithoutStackTrace(failureOf(new RuntimeException("one")))
        def second = converter.convertWithoutStackTrace(failureOf(new RuntimeException("two")))

        then:
        !first.is(second)
    }

    def "uses throwable identity rather than equality for cache keys"() {
        when:
        def first = converter.convertWithoutStackTrace(failureOf(new EqualException("one")))
        def second = converter.convertWithoutStackTrace(failureOf(new EqualException("two")))

        then:
        !first.is(second)
        first.message == "one"
        second.message == "two"
    }

    def "reuses the whole converted tree when two failures share the same throwable tree"() {
        def shared = chainOfDepth(4)

        when:
        def first = converter.convertWithoutStackTrace(failureOf(shared))
        def second = converter.convertWithoutStackTrace(failureOf(shared))

        then:
        first.is(second)
        first.causes[0].is(second.causes[0])
    }

    def "reuses a shared deep cause while keeping distinct top wrappers distinct"() {
        def sharedCause = new RuntimeException("shared included build failure")
        def topA = new RuntimeException("project :a failed", sharedCause)
        def topB = new RuntimeException("project :b failed", sharedCause)

        when:
        def a = converter.convertWithoutStackTrace(failureOf(topA))
        def b = converter.convertWithoutStackTrace(failureOf(topB))

        then: "the per-project wrappers differ but the shared deep cause is converted once"
        !a.is(b)
        a.causes[0].is(b.causes[0])
    }

    def "parallel conversions of a shared failure converge on one canonical instance"() {
        def shared = chainOfDepth(5)
        def threads = 50
        def executor = Executors.newFixedThreadPool(threads)
        def start = new CountDownLatch(1)

        when:
        def futures = (1..threads).collect {
            executor.submit({
                start.await()
                converter.convertWithoutStackTrace(failureOf(shared))
            } as Callable)
        }
        start.countDown()
        def results = futures.collect { it.get() }

        then: "every thread sees the same canonical conversion and none failed"
        results.every { it.is(results[0]) }

        cleanup:
        executor.shutdownNow()
    }

    /**
     * Whether the text renders a stack trace: a frame line, or the line that elides a tail of frames shared with the
     * parent. Both are indented, and a suppressed exception's own lines carry one further level of indentation.
     */
    private static boolean containsRenderedStackTrace(String text) {
        return text.readLines().any { it ==~ /\t+(at .+|\.\.\. \d+ more)/ }
    }

    private static Failure failureOf(Throwable throwable) {
        DefaultFailureFactory.withDefaultClassifier().create(throwable)
    }

    private static Throwable chainOfDepth(int depth) {
        Throwable t = new RuntimeException("leaf")
        (1..(depth - 1)).each { t = new RuntimeException("level-$it", t) }
        t
    }

    private static class CauseAccessGuardException extends RuntimeException {
        boolean causeAccessAllowed = true

        CauseAccessGuardException(String message) {
            super(message)
        }

        @Override
        synchronized Throwable getCause() {
            if (!causeAccessAllowed) {
                throw new AssertionError("cause should not be read after the throwable has been cached")
            }
            return super.getCause()
        }
    }

    private static class EqualException extends RuntimeException {
        EqualException(String message) {
            super(message)
        }

        @Override
        boolean equals(Object other) {
            return other instanceof EqualException
        }

        @Override
        int hashCode() {
            return 0
        }
    }
}
