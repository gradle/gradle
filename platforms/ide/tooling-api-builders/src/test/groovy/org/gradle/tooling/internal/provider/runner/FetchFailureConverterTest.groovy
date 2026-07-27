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

    def "reuses the converted failure when two failures share the same original throwable"() {
        def throwable = new RuntimeException("boom")

        when:
        def first = converter.convert(failureOf(throwable))
        def second = converter.convert(failureOf(throwable))

        then:
        first.is(second)
    }

    def "converts failures with distinct originals into distinct instances"() {
        when:
        def first = converter.convert(failureOf(new RuntimeException("one")))
        def second = converter.convert(failureOf(new RuntimeException("two")))

        then:
        !first.is(second)
    }

    def "reuses the whole converted tree when two failures share the same throwable tree"() {
        def shared = chainOfDepth(4)

        when:
        def first = converter.convert(failureOf(shared))
        def second = converter.convert(failureOf(shared))

        then:
        first.is(second)
        first.causes[0].is(second.causes[0])
    }

    def "reuses a shared deep cause while keeping distinct top wrappers distinct"() {
        def sharedCause = new RuntimeException("shared included build failure")
        def topA = new RuntimeException("project :a failed", sharedCause)
        def topB = new RuntimeException("project :b failed", sharedCause)

        when:
        def a = converter.convert(failureOf(topA))
        def b = converter.convert(failureOf(topB))

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
                converter.convert(failureOf(shared))
            } as Callable)
        }
        start.countDown()
        def results = futures.collect { it.get() }

        then: "every thread sees the same canonical conversion and none failed"
        results.every { it.is(results[0]) }

        cleanup:
        executor.shutdownNow()
    }

    private static Failure failureOf(Throwable throwable) {
        DefaultFailureFactory.withDefaultClassifier().create(throwable)
    }

    private static Throwable chainOfDepth(int depth) {
        Throwable t = new RuntimeException("leaf")
        (1..(depth - 1)).each { t = new RuntimeException("level-$it", t) }
        t
    }
}
