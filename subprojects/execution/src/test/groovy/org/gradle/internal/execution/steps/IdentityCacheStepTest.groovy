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

    def "executes when no cached result exists"() {
        def output = Mock(Object)
        def result = Mock(Object)
        def delegateResult = Stub(Result) {
            getExecutionResult() >> Try.successful(Stub(Result.ExecutionResult) {
                getOutput() >> output
            })
        }

        when:
        def actualResult = step.executeDeferred(context, cache, processor)

        then:
        actualResult == result
        1 * processor.processDeferredResult(_) >> { Supplier<Try<Object>> deferredExecution ->
            assert deferredExecution.get().get() == output
            return result
        }

        then:
        _ * delegate.execute(context) >> delegateResult
        0 * _
    }

    def "returns cached result when exists"() {
        def output = Mock(Try)
        def result = Mock(Object)

        cache.put(identity, output)

        when:
        def actualResult = step.executeDeferred(context, cache, processor)

        then:
        actualResult == result
        1 * processor.processCachedResult(output) >> result
        0 * _
    }
}
