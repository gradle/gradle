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

import org.gradle.internal.execution.WorkValidationContext
import org.gradle.internal.execution.WorkValidationException
import org.gradle.internal.execution.impl.DefaultWorkValidationContext
import org.gradle.internal.vfs.VirtualFileSystem

import static org.gradle.internal.reflect.TypeValidationContext.Severity.ERROR
import static org.gradle.internal.reflect.TypeValidationContext.Severity.WARNING

class ValidateStepTest extends StepSpec<AfterPreviousExecutionContext> {
    def warningReporter = Mock(ValidateStep.ValidationWarningRecorder)
    def virtualFileSystem = Mock(VirtualFileSystem)
    def step = new ValidateStep<>(virtualFileSystem, warningReporter, delegate)
    def delegateResult = Mock(Result)

    @Override
    protected AfterPreviousExecutionContext createContext() {
        def validationContext = new DefaultWorkValidationContext()
        return Stub(AfterPreviousExecutionContext) {
            getValidationContext() >> validationContext
        }
    }

    def "executes work when there are no violations"() {
        boolean validated = false
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        1 * delegate.execute(work, { ValidationContext context -> !context.validationProblems.present }) >> delegateResult
        _ * work.validate(_ as  WorkValidationContext) >> { validated = true }

        then:
        validated
        0 * _
    }

    def "fails when there is a single violation"() {
        when:
        step.execute(work, context)

        then:
        def ex = thrown WorkValidationException
        ex.message == "A problem was found with the configuration of job ':test' (type 'ValidateStepTest.JobType')."
        ex.causes.size() == 1
        ex.causes[0].message == "Type '$Object.simpleName': Validation error."

        _ * work.validate(_ as  WorkValidationContext) >> {  WorkValidationContext validationContext ->
            validationContext.forType(JobType, true).visitTypeProblem(ERROR, Object, "Validation error")
        }
        0 * _
    }

    def "fails when there are multiple violations"() {
        when:
        step.execute(work, context)

        then:
        def ex = thrown WorkValidationException
        ex.message == "Some problems were found with the configuration of job ':test' (types 'ValidateStepTest.JobType', 'ValidateStepTest.SecondaryJobType')."
        ex.causes.size() == 2
        ex.causes[0].message == "Type '$Object.simpleName': Validation error #1."
        ex.causes[1].message == "Type '$Object.simpleName': Validation error #2."

        _ * work.validate(_ as  WorkValidationContext) >> {  WorkValidationContext validationContext ->
            validationContext.forType(JobType, true).visitTypeProblem(ERROR, Object, "Validation error #1")
            validationContext.forType(SecondaryJobType, true).visitTypeProblem(ERROR, Object, "Validation error #2")
        }
        0 * _
    }

    def "reports deprecation warning and invalidates VFS for validation warning"() {
        String expectedWarning = "Type '$Object.simpleName': Validation warning."
        when:
        step.execute(work, context)

        then:
        _ * work.validate(_ as  WorkValidationContext) >> {  WorkValidationContext validationContext ->
            validationContext.forType(JobType, true).visitTypeProblem(WARNING, Object, "Validation warning")
        }

        then:
        1 * warningReporter.recordValidationWarnings(work, { warnings -> warnings == [expectedWarning] })
        1 * virtualFileSystem.invalidateAll()

        then:
        1 * delegate.execute(work, { ValidationContext context -> context.validationProblems.get().warnings == [expectedWarning] })
        0 * _
    }

    def "reports deprecation warning even when there's also an error"() {
        when:
        step.execute(work, context)

        then:
        _ * work.validate(_ as  WorkValidationContext) >> {  WorkValidationContext validationContext ->
            def typeContext = validationContext.forType(JobType, true)
            typeContext.visitTypeProblem(ERROR, Object, "Validation error")
            typeContext.visitTypeProblem(WARNING, Object, "Validation warning")
        }

        then:
        1 * warningReporter.recordValidationWarnings(work, { warnings -> warnings == ["Type '$Object.simpleName': Validation warning."]})

        then:
        def ex = thrown WorkValidationException
        ex.message == "A problem was found with the configuration of job ':test' (type 'ValidateStepTest.JobType')."
        ex.causes.size() == 1
        ex.causes[0].message == "Type '$Object.simpleName': Validation error."
        0 * _
    }

    interface JobType {}

    interface SecondaryJobType {}
}
