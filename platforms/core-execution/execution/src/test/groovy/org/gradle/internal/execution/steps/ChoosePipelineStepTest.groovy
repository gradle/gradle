/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.internal.execution.ImmutableUnitOfWork
import org.gradle.internal.execution.MutableUnitOfWork

class ChoosePipelineStepTest extends StepSpec<IdentityContext> {
    def incrementalPipeline = Mock(Step)
    def nonIncrementalPipeline = Mock(Step)
    def step = new ChoosePipelineStep(nonIncrementalPipeline, incrementalPipeline)

    def "executes non-incremental work via non-incremental pipeline"() {
        def delegateResult = Mock(Result)
        def nonIncrementalWork = Mock(ImmutableUnitOfWork)

        when:
        def result = step.execute(nonIncrementalWork, context)

        then:
        result == delegateResult
        1 * nonIncrementalPipeline.execute(nonIncrementalWork, context) >> delegateResult
        0 * _
    }

    def "executes incremental work via incremental pipeline"() {
        def delegateResult = Mock(Result)
        def incrementalWork = Mock(MutableUnitOfWork)

        when:
        def result = step.execute(incrementalWork, context)

        then:
        result == delegateResult
        1 * incrementalPipeline.execute(incrementalWork, context) >> delegateResult
        0 * _
    }

    def "barfs at unknown type of work"() {
        when:
        step.execute(work, context)

        then:
        def ex = thrown AssertionError
        ex.message.startsWith("Invalid work type: ${work.class.name}")
    }
}
