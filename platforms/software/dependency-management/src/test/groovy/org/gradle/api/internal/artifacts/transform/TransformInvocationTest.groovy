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

package org.gradle.api.internal.artifacts.transform

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.internal.Try
import org.gradle.internal.execution.DeferredExecution
import spock.lang.Specification

import java.util.function.Supplier

class TransformInvocationTest extends Specification {
    def subject = TransformStepSubject.initial(Stub(ResolvableArtifact))

    def "known result is available without invoking"() {
        def invocation = TransformInvocation.known(Try.successful(subject))

        expect:
        invocation.resultWithoutInvoking.get().get().is(subject)
        invocation.invoke().get().is(subject)
    }

    def "invocation of a cache hit is known without invoking"() {
        def invocation = TransformInvocation.of(DeferredExecution.cacheHit(Try.successful(subject)))

        expect:
        invocation.resultWithoutInvoking.get().get().is(subject)
        invocation.invoke().get().is(subject)
    }

    def "invocation of a cache miss executes it only when invoked"() {
        def work = Mock(Supplier)

        when:
        def invocation = TransformInvocation.of(DeferredExecution.cacheMiss(work))

        then:
        !invocation.resultWithoutInvoking.present
        0 * work._

        when:
        def result = invocation.invoke()

        then:
        1 * work.get() >> Try.successful(subject)
        result.get().is(subject)
        !invocation.resultWithoutInvoking.present
    }

    def "deferred invocation runs its work only when invoked"() {
        def work = Mock(Supplier)

        when:
        def invocation = TransformInvocation.deferred(work)

        then:
        !invocation.resultWithoutInvoking.present
        0 * work._

        when:
        def result = invocation.invoke()

        then:
        1 * work.get() >> Try.successful(subject)
        result.get().is(subject)
    }
}
