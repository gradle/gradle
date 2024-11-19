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

import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.api.problems.Problem
import org.gradle.api.problems.internal.ProblemsProgressEventEmitterHolder
import org.gradle.internal.execution.WorkValidationContext
import org.gradle.internal.execution.WorkValidationException
import org.gradle.internal.execution.WorkValidationExceptionChecker
import org.gradle.internal.execution.impl.DefaultWorkValidationContext
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.vfs.VirtualFileSystem
import org.gradle.util.TestUtil

import static com.google.common.collect.ImmutableList.of
import static org.gradle.internal.RenderingUtils.quotedOxfordListOf
import static org.gradle.internal.deprecation.Documentation.userManual
import static org.gradle.internal.reflect.validation.TypeValidationProblemRenderer.convertToSingleLine
import static org.gradle.internal.reflect.validation.TypeValidationProblemRenderer.renderMinimalInformationAbout

class ValidateStepTest extends StepSpec<BeforeExecutionContext> implements ValidationMessageChecker {

    def warningReporter = Mock(ValidateStep.ValidationWarningRecorder)
    def virtualFileSystem = Mock(VirtualFileSystem)
    def buildOperationProgressEventEmitter = Mock(BuildOperationProgressEventEmitter)
    def step = new ValidateStep<>(virtualFileSystem, warningReporter, delegate)
    def delegateResult = Mock(Result)

    def setup() {
        def validationContext = new DefaultWorkValidationContext(WorkValidationContext.TypeOriginInspector.NO_OP)
        context.getValidationContext() >> validationContext
        ProblemsProgressEventEmitterHolder.init(TestUtil.problemsService())
    }

    def "executes work when there are no violations"() {
        boolean validated = false
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        1 * delegate.execute(work, { ValidationFinishedContext context -> context.validationProblems.empty }) >> delegateResult
        _ * work.validate(_ as WorkValidationContext) >> { validated = true }

        then:
        validated
        0 * _
    }

    def "fails when there is a single violation"() {
        expectReindentedValidationMessage()
        when:
        step.execute(work, context)

        then:
        def ex = thrown(WorkValidationException)
        WorkValidationExceptionChecker.check(ex) {
            def validationProblem = dummyPropertyValidationProblemWithLink('java.lang.Object', null, 'Validation error', 'Test').trim()
            hasMessage """A problem was found with the configuration of job ':test' (type 'ValidateStepTest.JobType').
  - ${validationProblem}"""
        }
        _ * work.validate(_ as WorkValidationContext) >> { WorkValidationContext validationContext ->
            validationContext.forType(JobType, true).visitTypeProblem {
                it
                    .withAnnotationType(Object)
                    .id("test-problem", "Validation error", GradleCoreProblemGroup.validation())
                    .documentedAt(userManual("id", "section"))
                    .details("Test")
                    .severity(Severity.ERROR)
            }
        }
        0 * _
        _ * buildOperationProgressEventEmitter.emitNowIfCurrent(_ as Object) >> {}
    }

    def "fails when there are multiple violations"() {
        expectReindentedValidationMessage()
        when:
        step.execute(work, context)

        then:
        def ex = thrown WorkValidationException
        WorkValidationExceptionChecker.check(ex) {
            def validationProblem1 = dummyPropertyValidationProblemWithLink('java.lang.Object', null, 'Validation error #1', 'Test')
            def validationProblem2 = dummyPropertyValidationProblemWithLink('java.lang.Object', null, 'Validation error #2', 'Test')
            hasMessage """Some problems were found with the configuration of job ':test' (types ${quotedOxfordListOf(of('ValidateStepTest.JobType', 'ValidateStepTest.SecondaryJobType'), 'and')}).
  - ${validationProblem1.trim()}
  - ${validationProblem2.trim()}"""
        }

        _ * work.validate(_ as WorkValidationContext) >> { WorkValidationContext validationContext ->
            validationContext.forType(JobType, true).visitTypeProblem {
                it
                    .withAnnotationType(Object)
                    .id("test-problem-1", "Validation error #1", GradleCoreProblemGroup.validation())
                    .documentedAt(userManual("id", "section"))
                    .severity(Severity.ERROR)
                    .details("Test")
            }
            validationContext.forType(SecondaryJobType, true).visitTypeProblem {
                it
                    .withAnnotationType(Object)
                    .id("test-problem-2", "Validation error #2", GradleCoreProblemGroup.validation())
                    .documentedAt(userManual("id", "section"))
                    .severity(Severity.ERROR)
                    .details("Test")
            }
        }
        0 * _
        _ * buildOperationProgressEventEmitter.emitNowIfCurrent(_ as Object) >> {}
    }

    def "reports deprecation warning and invalidates VFS for validation warning"() {
        String expectedWarning = convertToSingleLine(dummyValidationProblem('java.lang.Object', null, 'Validation warning', 'Test').trim())
        when:
        step.execute(work, context)

        then:
        _ * work.validate(_ as WorkValidationContext) >> { WorkValidationContext validationContext ->
            validationContext.forType(JobType, true).visitTypeProblem {
                it
                    .withAnnotationType(Object)
                    .id("test-problem", "Validation warning", GradleCoreProblemGroup.validation())
                    .documentedAt(userManual("id", "section"))
                    .severity(Severity.WARNING)
                    .details("Test")
            }
        }

        then:
        _ * buildOperationProgressEventEmitter.emitNowIfCurrent(_ as Object) >> {}
        1 * warningReporter.recordValidationWarnings(work, { List<Problem> warnings ->
            convertToSingleLine(renderMinimalInformationAbout(warnings.first(), false, false)) == expectedWarning
        })
        1 * virtualFileSystem.invalidateAll()

        then:
        1 * delegate.execute(work, { ValidationFinishedContext context ->
            convertToSingleLine(renderMinimalInformationAbout(context.validationProblems.first(), false, false)) == expectedWarning
        })
        0 * _
    }

    def "reports deprecation warning even when there's also an error"() {
        String expectedWarning = convertToSingleLine(dummyPropertyValidationProblemWithLink('java.lang.Object', null, 'Validation problem', 'Test').trim())
        // errors are reindented but not warnings
        expectReindentedValidationMessage()
        String expectedError = dummyPropertyValidationProblemWithLink('java.lang.Object', null, 'Validation problem', 'Test')

        when:
        step.execute(work, context)

        then:
        _ * work.validate(_ as WorkValidationContext) >> { WorkValidationContext validationContext ->
            def typeContext = validationContext.forType(JobType, true)
            typeContext.visitTypeProblem {
                it
                    .withAnnotationType(Object)
                    .id("test-problem", "Validation problem", GradleCoreProblemGroup.validation())
                    .documentedAt(userManual("id", "section"))
                    .severity(Severity.ERROR)
                    .details("Test")
            }
            typeContext.visitTypeProblem {
                it
                    .withAnnotationType(Object)
                    .id("test-problem", "Validation problem", GradleCoreProblemGroup.validation())
                    .documentedAt(userManual("id", "section"))
                    .severity(Severity.WARNING)
                    .details("Test")
            }
        }

        then:
        _ * buildOperationProgressEventEmitter.emitNowIfCurrent(_ as Object) >> {}
        1 * warningReporter.recordValidationWarnings(work, { warnings -> convertToSingleLine(renderMinimalInformationAbout(warnings.first(), true, false)) == expectedWarning })

        then:
        def ex = thrown WorkValidationException
        WorkValidationExceptionChecker.check(ex) {
            hasMessage """A problem was found with the configuration of job ':test' (type 'ValidateStepTest.JobType').
  - ${expectedError}"""
        }
        0 * _
    }

    interface JobType {}

    interface SecondaryJobType {}
}
