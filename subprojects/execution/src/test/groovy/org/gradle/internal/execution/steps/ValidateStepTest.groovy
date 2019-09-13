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

import org.gradle.internal.execution.Result
import org.gradle.internal.reflect.WorkValidationContext
import org.gradle.internal.reflect.WorkValidationException

class ValidateStepTest extends ContextInsensitiveStepSpec {
    def warningReporter = Mock(ValidateStep.ValidationWarningReporter)
    def step = new ValidateStep<>(warningReporter, delegate)
    def delegateResult = Mock(Result)

    def "executes work when there are no violations"() {
        boolean validated = false
        when:
        def result = step.execute(context)

        then:
        result == delegateResult

        1 * delegate.execute(_) >> { ctx ->
            delegateResult
        }
        _ * work.validate(_ as WorkValidationContext) >> { validated = true }

        then:
        validated
        0 * _
    }

    def "fails when there is a single violation"() {
        when:
        step.execute(context)

        then:
        def ex = thrown WorkValidationException
        ex.message == "A problem was found with the configuration of job ':test'."
        ex.causes.size() == 1
        ex.causes[0].message == "Validation error"

        _ * work.validate(_ as WorkValidationContext) >> { WorkValidationContext validationContext ->
            validationContext.visitError("Validation error")
        }
        0 * _
    }

    def "fails when there are multiple violations"() {
        when:
        step.execute(context)

        then:
        def ex = thrown WorkValidationException
        ex.message == "Some problems were found with the configuration of job ':test'."
        ex.causes.size() == 2
        ex.causes[0].message == "Validation error #1"
        ex.causes[1].message == "Validation error #2"

        _ * work.validate(_ as WorkValidationContext) >> { WorkValidationContext validationContext ->
            validationContext.visitError("Validation error #1")
            validationContext.visitError("Validation error #2")
        }
        0 * _
    }

    def "reports deprecation warning for validation warning"() {
        when:
        step.execute(context)

        then:
        _ * work.validate(_ as WorkValidationContext) >> { WorkValidationContext validationContext ->
            validationContext.visitWarning("Validation warning")
        }

        then:
        1 * warningReporter.reportValidationWarning("Validation warning")

        then:
        1 * delegate.execute(context)
        0 * _
    }

    def "reports deprecation warning even when there's also an error"() {
        when:
        step.execute(context)

        then:
        _ * work.validate(_ as WorkValidationContext) >> { WorkValidationContext validationContext ->
            validationContext.visitWarning("Validation warning")
            validationContext.visitError("Validation error")
        }

        then:
        1 * warningReporter.reportValidationWarning("Validation warning")

        then:
        def ex = thrown WorkValidationException
        ex.message == "A problem was found with the configuration of job ':test'."
        ex.causes.size() == 1
        ex.causes[0].message == "Validation error"
        0 * _
    }
}
