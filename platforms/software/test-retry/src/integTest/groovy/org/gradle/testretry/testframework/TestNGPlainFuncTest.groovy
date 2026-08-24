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
package org.gradle.testretry.testframework

import javax.annotation.Nullable

class TestNGPlainFuncTest extends BaseTestNGFuncTest {

    @Override
    String reportedLifecycleMethodName(TestNGLifecycleType lifecycleType, String methodName) {
        methodName
    }

    @Override
    String reportedParameterizedMethodName(String methodName, String paramType, int invocationNumber, @Nullable String paramValueRepresentation) {
        "${methodName}[${invocationNumber}]${paramValueRepresentation ? "(${paramValueRepresentation})" : ""}"
    }

    @Override
    boolean reportsSuccessfulLifecycleExecutions(TestNGLifecycleType lifecycleType) {
        true
    }

    /**
     * If JUnit's TestNG engine is used, then tests won't even run and the failure is silently swallowed.
     */
    @spock.lang.PendingFeature(reason = "Plugin emits a NullPointerException stack trace via TestsReader when className is null during static initializer failure, which AbstractIntegrationSpec rejects as an unexpected stack trace")
    def "does not handle flaky static initializers"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            public class SomeTests {

                static {
                    ${flakyAssert()}
                }

                @org.testng.annotations.Test
                public void someTest() {}
            }
        """

        when:
        fails('test')

        then:
        with(output) {
            // Gradle 9 detects this as a fatal test framework error
            assert it.contains('Could not complete execution for Gradle Test Executor')
            assert !it.contains('The following test methods could not be retried')
        }
    }
}
