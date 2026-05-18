/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.execution.impl

import org.gradle.api.problems.Problem
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.ProblemsProgressEventEmitterHolder
import org.gradle.internal.execution.Identity
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.WorkValidationContext
import org.gradle.internal.execution.WorkValidationException
import org.gradle.internal.execution.steps.ValidateStep
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.vfs.VirtualFileSystem
import org.gradle.util.TestUtil
import spock.lang.Specification

import static com.google.common.collect.ImmutableList.of
import static org.gradle.internal.RenderingUtils.quotedOxfordListOf
import static org.gradle.internal.deprecation.Documentation.userManual
import static org.gradle.internal.reflect.validation.TypeValidationProblemRenderer.convertToSingleLine
import static org.gradle.internal.reflect.validation.TypeValidationProblemRenderer.renderMinimalInformationAbout

class DefaultExecutionProblemHandlerTest extends Specification implements ValidationMessageChecker {

    def identity = Mock(Identity)
    def work = Stub(UnitOfWork)
    def warningReporter = Mock(ValidateStep.ValidationWarningRecorder)
    def virtualFileSystem = Mock(VirtualFileSystem)
    def problems = TestUtil.problemsService()
    def validationContext = new DefaultWorkValidationContext(WorkValidationContext.TypeOriginInspector.NO_OP, problems)
    def handler = new DefaultExecutionProblemHandler(warningReporter, virtualFileSystem)

    def setup() {
        ProblemsProgressEventEmitterHolder.init(problems)
        work.displayName >> "job ':test'"
    }

    def cleanup() {
        ProblemsProgressEventEmitterHolder.clear()
    }

    def "fails when there is a single violation"() {
        expectReindentedValidationMessage()
        given:
        validationContext.forType(JobType, true).visitTypeError {
            it
                .withAnnotationType(Object)
                .id(ProblemId.create("test-problem", "Validation error", GradleCoreProblemGroup.validation().type()))
                .documentedAt(userManual("id", "section"))
                .details("Test")
        }

        when:
        handler.handleReportedProblems(identity, work, validationContext)

        then:
        def ex = thrown(WorkValidationException)
        ex.message == "A problem was found with the configuration of job ':test' (type 'DefaultExecutionProblemHandlerTest.JobType')."
        problems.assertProblemEmittedOnce({
            it.definition.severity == Severity.ERROR
            it.definition.id.name == 'test-problem'
            it.definition.id.displayName == 'Validation error'
            it.contextualLabel == "Type 'java.lang.Object' Validation error"
            it.details == 'Test'
            it.definition.documentationLink.url.endsWith('/userguide/id.html#section')
            it.solutions == []
            it.originLocations == []
        })
        0 * _
    }

    def "fails when there are multiple violations"() {
        expectReindentedValidationMessage()
        given:
        validationContext.forType(JobType, true).visitTypeError {
            it
                .withAnnotationType(Object)
                .id(ProblemId.create("test-problem-1", "Validation error #1", GradleCoreProblemGroup.validation().type()))
                .documentedAt(userManual("id", "section"))
                .details("Test")
        }
        validationContext.forType(SecondaryJobType, true).visitTypeError {
            it
                .withAnnotationType(Object)
                .id(ProblemId.create("test-problem-2", "Validation error #2", GradleCoreProblemGroup.validation().type()))
                .documentedAt(userManual("id", "section"))
                .details("Test")
        }
        when:
        handler.handleReportedProblems(identity, work, validationContext)

        then:
        def ex = thrown WorkValidationException
        ex.message == "Some problems were found with the configuration of job ':test' (types ${quotedOxfordListOf(of('DefaultExecutionProblemHandlerTest.JobType', 'DefaultExecutionProblemHandlerTest.SecondaryJobType'), 'and')})."

        problems.emitted.size() == 2
        verifyAll(problems.emitted[0]) {
            definition.severity == Severity.ERROR
            definition.id.name == 'test-problem-1'
            definition.id.displayName == 'Validation error #1'
            contextualLabel == "Type 'java.lang.Object' Validation error #1"
            details == 'Test'
            definition.documentationLink.url.endsWith('/userguide/id.html#section')
            solutions == []
            originLocations == []
        }
        verifyAll(problems.emitted[1]) {
            definition.severity == Severity.ERROR
            definition.id.name == 'test-problem-2'
            definition.id.displayName == 'Validation error #2'
            contextualLabel == "Type 'java.lang.Object' Validation error #2"
            details == 'Test'
            definition.documentationLink.url.endsWith('/userguide/id.html#section')
            solutions == []
            originLocations == []
        }

        0 * _
    }

    def "reports deprecation warning and invalidates VFS for validation warning"() {
        String expectedWarning = convertToSingleLine(dummyValidationProblem('java.lang.Object', null, 'Validation warning', 'Test').trim())
        given:
        validationContext.forType(JobType, true).visitTypeWarning {
            it
                .withAnnotationType(Object)
                .id(ProblemId.create("test-problem", "Validation warning", GradleCoreProblemGroup.validation().type()))
                .documentedAt(userManual("id", "section"))
                .details("Test")
        }
        when:
        handler.handleReportedProblems(identity, work, validationContext)

        then:
        1 * warningReporter.recordValidationWarnings(identity, work, { List<Problem> warnings ->
            convertToSingleLine(renderMinimalInformationAbout(warnings.first() as ProblemInternal, false, false)) == expectedWarning
        })
        problems.assertProblemEmittedOnce({
            it.definition.severity == Severity.WARNING
            it.definition.id.name == 'test-problem'
            it.definition.id.displayName == 'Validation warning'
            it.contextualLabel == "Type 'java.lang.Object' Validation warning"
            it.details == 'Test'
            it.definition.documentationLink.url.endsWith('/userguide/id.html#section')
            it.solutions == []
            it.originLocations == []
        })

        then:
        1 * virtualFileSystem.invalidateAll()
        0 * _
    }

    def "reports deprecation warning even when there's also an error"() {
        String expectedWarning = convertToSingleLine(dummyPropertyValidationProblemWithLink('java.lang.Object', null, 'Validation problem', 'Test').trim())
        // errors are reindented but not warnings
        expectReindentedValidationMessage()
        String expectedError = dummyPropertyValidationProblemWithLink('java.lang.Object', null, 'Validation problem', 'Test')

        given:
        def typeContext = validationContext.forType(JobType, true)
        typeContext.visitTypeError {
            it
                .withAnnotationType(Object)
                .id(ProblemId.create("test-problem", "Validation problem", GradleCoreProblemGroup.validation().type()))
                .documentedAt(userManual("id", "section"))
                .details("Test")
        }
        typeContext.visitTypeWarning {
            it
                .withAnnotationType(Object)
                .id(ProblemId.create("test-problem", "Validation problem", GradleCoreProblemGroup.validation().type()))
                .documentedAt(userManual("id", "section"))
                .details("Test")
        }

        when:
        handler.handleReportedProblems(identity, work, validationContext)

        then:
        1 * warningReporter.recordValidationWarnings(identity, work, { warnings -> convertToSingleLine(renderMinimalInformationAbout(warnings.first() as ProblemInternal, true, false)) == expectedWarning })

        then:
        def ex = thrown WorkValidationException
        ex.message ==  "A problem was found with the configuration of job ':test' (type 'DefaultExecutionProblemHandlerTest.JobType')."
        problems.emitted.size() == 2
        verifyAll(problems.emitted[0]) {
            definition.severity == Severity.WARNING
            definition.id.name == 'test-problem'
            definition.id.displayName == 'Validation problem'
            contextualLabel == "Type 'java.lang.Object' Validation problem"
            details == 'Test'
            definition.documentationLink.url.endsWith('/userguide/id.html#section')
            solutions == []
            originLocations == []
        }
        verifyAll(problems.emitted[1]) {
            definition.severity == Severity.ERROR
            definition.id.name == 'test-problem'
            definition.id.displayName == 'Validation problem'
            contextualLabel == "Type 'java.lang.Object' Validation problem"
            details == 'Test'
            definition.documentationLink.url.endsWith('/userguide/id.html#section')
            solutions == []
            originLocations == []
        }
        0 * _
    }

    interface JobType {}

    interface SecondaryJobType {}
}
