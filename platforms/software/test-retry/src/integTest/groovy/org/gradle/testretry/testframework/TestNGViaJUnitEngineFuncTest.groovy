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

import static org.gradle.testretry.testframework.BaseTestNGFuncTest.TestNGLifecycleType.AFTER_CLASS
import static org.gradle.testretry.testframework.BaseTestNGFuncTest.TestNGLifecycleType.AFTER_TEST
import static org.gradle.testretry.testframework.BaseTestNGFuncTest.TestNGLifecycleType.BEFORE_TEST

class TestNGViaJUnitEngineFuncTest extends BaseTestNGFuncTest {

    private static final EnumSet<TestNGLifecycleType> UNREPORTED_LIFECYCLE_METHODS = EnumSet.of(BEFORE_TEST, AFTER_TEST, AFTER_CLASS)

    def setup() {
        buildFile << """
            dependencies {
                testImplementation 'org.testng:testng:7.5'
                testRuntimeOnly 'org.junit.support:testng-engine:1.0.5'
                // Since Gradle 9, the JUnit platform launcher is no longer provided by Gradle.
                testRuntimeOnly '${junitPlatformLauncherDependency()}'
            }
            test {
                useJUnitPlatform()
            }
        """
    }

    @Override
    String reportedLifecycleMethodName(TestNGLifecycleType lifecycleType, String methodName) {
        "executionError"
    }

    @Override
    String reportedParameterizedMethodName(String methodName, String paramType, int invocationNumber, @Nullable String paramValueRepresentation) {
        "${methodName}(${paramType}) > [${invocationNumber}] ${paramValueRepresentation ?: ''}"
    }

    @Override
    boolean reportsSuccessfulLifecycleExecutions(TestNGLifecycleType lifecycleType) {
        !UNREPORTED_LIFECYCLE_METHODS.contains(lifecycleType)
    }

    def "retries all classes if failure occurs in #lifecycle"(TestNGLifecycleType lifecycle) {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            public class SuccessfulTestsWithFailingLifecycle {
                @org.testng.annotations.${lifecycle.annotation}
                public ${lifecycle.annotation.contains('Class') ? 'static ' : ''}void lifecycle() {
                    ${flakyAssert()}
                }

                @org.testng.annotations.Test
                public void successTestWithLifecycle() {}
            }
        """

        writeJavaTestSource """
            package acme;

            public class SuccessfulTestsPotentiallyDependingOnLifecycle {
                @org.testng.annotations.Test
                public void successTest() {}
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            // if BeforeTest fails, then methods won't be executed
            assert it.count('successTest SKIPPED') == (lifecycle == BEFORE_TEST ? 1 : 0)
            assert it.count('successTestWithLifecycle SKIPPED') == (lifecycle == BEFORE_TEST ? 1 : 0)

            assert it.count('successTest PASSED') == (lifecycle == BEFORE_TEST ? 1 : 2)
            assert it.count('successTestWithLifecycle PASSED') == (lifecycle == BEFORE_TEST ? 1 : 2)
            assert !it.contains("The following test methods could not be retried")
        }

        where:
        lifecycle << UNREPORTED_LIFECYCLE_METHODS
    }
}
