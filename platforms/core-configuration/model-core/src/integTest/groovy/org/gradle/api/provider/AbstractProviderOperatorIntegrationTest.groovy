/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.hamcrest.CoreMatchers

abstract class AbstractProviderOperatorIntegrationTest extends AbstractIntegrationSpec {
    protected static final String RESULT_PREFIX = "Result: "

    protected static FailureWithCause unsupportedWithCause(String failureCause) {
        return new FailureWithCause(failureCause)
    }

    protected static FailureWithDescription unsupportedWithDescription(String error) {
        return new FailureWithDescription(error)
    }

    protected void runAndAssert(String task, Object expectedResult) {
        if (expectedResult instanceof Failure) {
            def failure = runAndFail(task)
            expectedResult.assertHasExpectedFailure(failure)
        } else {
            run(task)
            outputContains(RESULT_PREFIX + expectedResult)
        }
    }

    private static interface Failure {
        void assertHasExpectedFailure(ExecutionFailure failure);
    }

    static class FailureWithCause implements Failure {
        final String failureCause

        FailureWithCause(String failureCause) {
            this.failureCause = failureCause
        }

        @Override
        void assertHasExpectedFailure(ExecutionFailure failure) {
            failure.assertHasCause(failureCause)
        }
    }

    static class FailureWithDescription implements Failure {
        final String failureDescription

        FailureWithDescription(String failureDescription) {
            this.failureDescription = failureDescription
        }

        @Override
        void assertHasExpectedFailure(ExecutionFailure failure) {
            failure.assertThatDescription(CoreMatchers.containsString(failureDescription))
        }
    }
}
