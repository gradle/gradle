/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.internal.exceptions


import spock.lang.Specification

class DefaultMultiCauseExceptionTest extends Specification {
    def getCauseReturnsTheFirstCause() {
        def cause1 = new RuntimeException()
        def cause2 = new RuntimeException()
        def failure = new TestMultiCauseException('message', [cause1, cause2])

        expect:
        failure.cause == cause1
        failure.causes == [cause1, cause2]
    }

    def getCauseReturnsNullWhenThereAreNoCauses() {
        def failure = new TestMultiCauseException('message', [])

        expect:
        failure.cause == null
        failure.causes == []
    }

    def canUseInitCauseToProvideCause() {
        def cause1 = new RuntimeException()
        def failure = new TestMultiCauseException('message', [])
        failure.initCause(cause1)

        expect:
        failure.cause == cause1
        failure.causes == [cause1]
    }

    def canUseInitCausesToProvideMultipleCause() {
        def cause1 = new RuntimeException()
        def cause2 = new RuntimeException()
        def failure = new TestMultiCauseException('message', [])
        failure.initCauses([cause1, cause2])

        expect:
        failure.cause == cause1
        failure.causes == [cause1, cause2]
    }

    def printStackTraceWithMultipleCauses() {
        RuntimeException cause1 = new RuntimeException('cause1')
        RuntimeException cause2 = new RuntimeException('cause2')
        def failure = new TestMultiCauseException('message', [cause1, cause2])
        def outstr = new StringWriter()

        when:
        outstr.withPrintWriter { writer ->
            failure.printStackTrace(writer)
        }

        then:
        outstr.toString().contains("${TestMultiCauseException.name}: message")
        outstr.toString().contains("Cause 1: ${RuntimeException.name}: cause1")
        outstr.toString().contains("Cause 2: ${RuntimeException.name}: cause2")
    }

    def printStackTraceWithSingleCause() {
        RuntimeException cause1 = new RuntimeException('cause1')
        def failure = new TestMultiCauseException('message', [cause1])
        def outstr = new StringWriter()

        when:
        outstr.withPrintWriter { writer ->
            failure.printStackTrace(writer)
        }

        then:
        outstr.toString().contains("${TestMultiCauseException.name}: message")
        outstr.toString().contains("Caused by: ${RuntimeException.name}: cause1")
    }

    def printStacktraceWithNestedMultipleCauses() {
        RuntimeException causeA = new RuntimeException('causeA')
        RuntimeException causeB = new RuntimeException('causeB')
        RuntimeException cause1 = new TestMultiCauseException('cause1', [causeA, causeB])
        RuntimeException causeC = new RuntimeException('causeC')
        RuntimeException causeD = new RuntimeException('causeD')
        RuntimeException cause2 = new TestMultiCauseException('cause2', [causeC, causeD])
        def failure = new TestMultiCauseException("BOOM", [new TestMultiCauseException('message', [cause1, cause2])])
        def outstr = new StringWriter()

        when:
        outstr.withPrintWriter { writer ->
            failure.printStackTrace(writer)
        }

        then:
        outstr.toString().contains("${TestMultiCauseException.name}: BOOM")
        outstr.toString().contains("Caused by: ${TestMultiCauseException.name}: message")
        outstr.toString().contains("Cause 1: ${TestMultiCauseException.name}: cause1")
        outstr.toString().contains("Cause 1: ${RuntimeException.name}: causeA")
        outstr.toString().contains("Cause 2: ${RuntimeException.name}: causeB")
        outstr.toString().contains("Cause 2: ${TestMultiCauseException.name}: cause2")
        outstr.toString().contains("Cause 1: ${RuntimeException.name}: causeC")
        outstr.toString().contains("Cause 2: ${RuntimeException.name}: causeD")
    }

    def canSerializeAndDeserializeException() {
        def cause1 = new RuntimeException("cause1")
        def cause2 = new RuntimeException("cause2")
        def failure = new TestMultiCauseException("message", [cause1, cause2])

        when:
        def baos = new ByteArrayOutputStream()
        new ObjectOutputStream(baos).writeObject(failure)
        def result = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray())).readObject()

        then:
        result instanceof TestMultiCauseException
        result.message == "message"
        result.causes.size() == 2
        result.causes*.message == ["cause1", "cause2"]
    }

    def 'when causes include ResolutionProviders, their resolutions are included'() {
        given:
        def fail1 = new TestResolutionProviderException('resolution1')
        def fail2 = new TestResolutionProviderException('resolution2')
        def multiFail = new DefaultMultiCauseException('failure', fail1, fail2)

        expect:multiFail.getResolutions() == ['resolution1', 'resolution2']
    }

    def 'when causes include nested ResolutionProviders, all resolutions are included'() {
        given:
        def childFail1 = new TestResolutionProviderException('resolutionChild')
        def childFail2 = new RuntimeException()
        def childMultiFail = new DefaultMultiCauseException('childFailure', childFail1, childFail2)

        def parentFail1 = new TestResolutionProviderException('resolutionParent')
        def parentFail2 = new RuntimeException()
        def parentMultiFail = new DefaultMultiCauseException('parentFailure', parentFail1, parentFail2, childMultiFail)

        expect:
        parentMultiFail.getResolutions() == ['resolutionParent', 'resolutionChild']
    }

    private static class TestMultiCauseException extends DefaultMultiCauseException {
        TestMultiCauseException(String message, Iterable<? extends Throwable> causes) {
            super(message, causes)
        }
    }

    private static class TestResolutionProviderException extends RuntimeException implements ResolutionProvider {
        private final String resolution

        TestResolutionProviderException(String resolution) {
            this.resolution = resolution
        }

        @Override
        List<String> getResolutions() {
            return Collections.singletonList(resolution)
        }
    }
}
