/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.execution.steps

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.gradle.internal.Try
import org.gradle.internal.execution.CachingResult
import org.gradle.internal.execution.DeferredResultProcessor
import org.gradle.internal.execution.IdentityContext
import org.gradle.internal.execution.Result
import org.gradle.internal.execution.UnitOfWork

import java.util.function.Supplier

class IdentityCacheStepTest extends StepSpec<IdentityContext> {
    Cache<UnitOfWork.Identity, Try<Object>> cache = CacheBuilder.newBuilder().build()

    def step = new IdentityCacheStep<>(delegate)
    def processor = Mock(DeferredResultProcessor)

    @Override
    protected IdentityContext createContext() {
        Stub(IdentityContext)
    }

    def "executes when no cached output exists"() {
        def processed = Mock(Object)
        def delegateOutput = Mock(Object)
        def delegateResult = Stub(CachingResult) {
            getExecutionResult() >> Try.successful(Stub(Result.ExecutionResult) {
                getOutput() >> delegateOutput
            })
        }

        when:
        def actual = step.executeDeferred(context, cache, processor)

        then:
        actual == processed
        1 * processor.processDeferredOutput(_) >> { Supplier<Try<Object>> deferredExecution ->
            assert deferredExecution.get().get() == delegateOutput
            return processed
        }

        then:
        _ * delegate.execute(context) >> delegateResult
        0 * _
    }

    def "returns cached output when exists"() {
        def cachedOutput = Try.successful(Mock(Object))
        def processed = Mock(Object)

        cache.put(identity, cachedOutput)

        when:
        def actual = step.executeDeferred(context, cache, processor)

        then:
        actual == processed
        1 * processor.processCachedOutput(cachedOutput) >> processed
        0 * _
    }
}
