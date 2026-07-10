/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.build.event.types

import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.problems.failure.DefaultFailureFactory
import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.problems.failure.FailurePrinter
import org.gradle.internal.problems.failure.StackFramePredicate
import org.gradle.internal.problems.failure.StackTraceRelevance
import org.gradle.tooling.internal.protocol.InternalFailure
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

class DefaultFailureTest extends Specification {

    def "empty problems list is returned for plain RuntimeException"() {
        when:
        def df = DefaultFailure.fromThrowable(new RuntimeException("test"))
        then:
        df.problems.empty
    }

    def "own description excludes descendant nodes while full description includes them"() {
        def failure = DefaultFailureFactory.withDefaultClassifier().create(deepChain())

        when:
        def root = DefaultFailure.fromFailure(failure, { p -> null } as Function)

        then:
        root.description.contains("level2-DEEP")
        !root.ownDescription.contains("level2-DEEP")

        and:
        def mid = root.causes[0]
        mid.description.contains("level2-DEEP")
        !mid.ownDescription.contains("level2-DEEP")
    }

    def "full description is built lazily without printing each node's subtree"() {
        def headerCount = new AtomicInteger()
        def source = new CountingFailure(DefaultFailureFactory.withDefaultClassifier().create(chainOfDepth(6)), headerCount)

        when:
        def root = DefaultFailure.fromFailure(source, { p -> null } as Function)

        then: "each of the 6 nodes is visited once, not once per ancestor (which would be quadratic)"
        headerCount.get() == 6

        and: "the full description is memoized"
        root.getDescription().is(root.getDescription())
    }

    def "lazy full description equals the printed failure for #scenario"() {
        def source = DefaultFailureFactory.withDefaultClassifier().create(throwable)
        def expected = FailurePrinter.printToString(source)

        when:
        def converted = DefaultFailure.fromFailure(source, { p -> null } as Function)

        then:
        converted.getDescription() == expected

        where:
        scenario                  | throwable
        "simple"                  | new RuntimeException("boom")
        "single cause"            | new RuntimeException("boom", new IllegalStateException("inner"))
        "suppressed"              | withSuppressed()
        // A tab-indented continuation line in a cause's message must not be mistaken for a stack frame, or the cause's
        // frames lose their common-tail elision against the parent and the text stops matching the direct print.
        "multi-line cause message" | new RuntimeException("outer", new IllegalStateException("inner\n\tindented detail"))
    }

    def "full description renders multiple causes"() {
        def source = DefaultFailureFactory.withDefaultClassifier().create(
            new DefaultMultiCauseException("multi", new RuntimeException("one"), new RuntimeException("two")))

        when:
        def text = DefaultFailure.fromFailure(source, { p -> null } as Function).getDescription()

        then:
        text.contains("Cause 1: java.lang.RuntimeException: one")
        text.contains("Cause 2: java.lang.RuntimeException: two")
    }

    def "an interior multi cause exception is dropped from both the cause structure and the description"() {
        def source = DefaultFailureFactory.withDefaultClassifier().create(
            new RuntimeException("outer", new DefaultMultiCauseException("INTERIOR-MULTI", new RuntimeException("branch-one"), new RuntimeException("branch-two"))))

        when:
        def converted = DefaultFailure.fromFailure(source, { p -> null } as Function)

        then: "the multi cause node is flattened away, promoting its children as direct causes"
        converted.causes.size() == 2

        and: "the reconstructed description follows that structure, so the multi cause node's header is absent"
        def text = converted.getDescription()
        !text.contains("INTERIOR-MULTI")
        text.contains("Cause 1: java.lang.RuntimeException: branch-one")
        text.contains("Cause 2: java.lang.RuntimeException: branch-two")

        and: "this intentionally diverges from a full recursive print, which keeps the multi cause node"
        FailurePrinter.printToString(source).contains("INTERIOR-MULTI")
    }

    def "converted failure survives serialization and reconstructs its description"() {
        def source = DefaultFailureFactory.withDefaultClassifier().create(new RuntimeException("boom", new IllegalStateException("inner")))
        def expected = FailurePrinter.printToString(source)
        def converted = DefaultFailure.fromFailure(source, { p -> null } as Function)

        when:
        def restored = roundTrip(converted)

        then:
        restored.getOwnDescription() == converted.getOwnDescription()
        restored.getDescription() == expected
    }

    private static InternalFailure roundTrip(InternalFailure failure) {
        def bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { it.writeObject(failure) }
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).withCloseable { (InternalFailure) it.readObject() }
    }

    private static Throwable withSuppressed() {
        def e = new RuntimeException("boom")
        e.addSuppressed(new IllegalStateException("suppressed-one"))
        e
    }

    private static Throwable chainOfDepth(int depth) {
        Throwable t = new RuntimeException("leaf")
        (1..(depth - 1)).each { t = new RuntimeException("level-$it", t) }
        t
    }

    private static Throwable deepChain() {
        new RuntimeException("root", level1())
    }

    private static Throwable level1() {
        new RuntimeException("level1", level2())
    }

    private static Throwable level2() {
        new RuntimeException("level2-DEEP")
    }

    private static class CountingFailure implements Failure {
        private final Failure delegate
        private final AtomicInteger headerCount

        CountingFailure(Failure delegate, AtomicInteger headerCount) {
            this.delegate = delegate
            this.headerCount = headerCount
        }

        Class<? extends Throwable> getExceptionType() { delegate.getExceptionType() }

        Throwable getOriginal() { delegate.getOriginal() }

        String getHeader() {
            headerCount.incrementAndGet()
            delegate.getHeader()
        }

        String getMessage() { delegate.getMessage() }

        List<StackTraceElement> getStackTrace() { delegate.getStackTrace() }

        StackTraceRelevance getStackTraceRelevance(int frameIndex) { delegate.getStackTraceRelevance(frameIndex) }

        List<Failure> getSuppressed() { delegate.getSuppressed().collect { new CountingFailure(it, headerCount) } }

        List<Failure> getCauses() { delegate.getCauses().collect { new CountingFailure(it, headerCount) } }

        int indexOfStackFrame(int start, StackFramePredicate predicate) { delegate.indexOfStackFrame(start, predicate) }

        List<ProblemInternal> getProblems() { delegate.getProblems() }
    }
}
