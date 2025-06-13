/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.internal.execution.ExecutionProblemHandler
import org.gradle.internal.execution.WorkValidationContext
import org.gradle.internal.execution.impl.DefaultWorkValidationContext
import org.gradle.util.TestUtil

class ValidateStepTest extends StepSpec<BeforeExecutionContext> {

    def problemHandler = Mock(ExecutionProblemHandler)
    def step = new ValidateStep<>(problemHandler, delegate)
    def delegateResult = Mock(Result)
    def problems = TestUtil.problemsService()

    def setup() {
        def validationContext = new DefaultWorkValidationContext(WorkValidationContext.TypeOriginInspector.NO_OP, problems)
        context.getValidationContext() >> validationContext
    }

    def "validates work"() {
        boolean validated = false
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        1 * delegate.execute(work, { ValidationFinishedContext context -> context.validationProblems.empty }) >> delegateResult
        _ * work.validate(_ as WorkValidationContext) >> { validated = true }
        1 * problemHandler.handleReportedProblems(identity, work, _ as WorkValidationContext)

        then:
        validated
        0 * _
    }

}
