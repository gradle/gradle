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

import org.gradle.util.GradleVersion

import javax.annotation.Nullable

import static org.gradle.testretry.testframework.BaseTestNGFuncTest.TestNGLifecycleType.AFTER_CLASS
import static org.gradle.testretry.testframework.BaseTestNGFuncTest.TestNGLifecycleType.AFTER_METHOD
import static org.gradle.testretry.testframework.BaseTestNGFuncTest.TestNGLifecycleType.AFTER_TEST
import static org.gradle.testretry.testframework.BaseTestNGFuncTest.TestNGLifecycleType.BEFORE_CLASS
import static org.gradle.testretry.testframework.BaseTestNGFuncTest.TestNGLifecycleType.BEFORE_METHOD
import static org.gradle.testretry.testframework.BaseTestNGFuncTest.TestNGLifecycleType.BEFORE_TEST

class TestNGViaJUnitEngineFuncTest extends BaseTestNGFuncTest {

    private static final EnumSet<TestNGLifecycleType> UNREPORTED_LIFECYCLE_METHODS = EnumSet.of(BEFORE_TEST, AFTER_TEST, AFTER_CLASS)
    private static final EnumSet<TestNGLifecycleType> CLASS_LIFECYCLE_METHODS = EnumSet.of(BEFORE_CLASS, BEFORE_METHOD, AFTER_METHOD)

    private static final GradleVersion GRADLE_5_0 = GradleVersion.version("5.0")
    private static final GradleVersion GRADLE_6_1 = GradleVersion.version("6.1")
    private static final GradleVersion GRADLE_7_0 = GradleVersion.version("7.0")
    private static final GradleVersion GRADLE_7_6_4 = GradleVersion.version("7.6.4")
    private static final GradleVersion GRADLE_8_0 = GradleVersion.version("8.0")
    private static final GradleVersion GRADLE_8_1 = GradleVersion.version("8.1")

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
    String reportedLifecycleMethodName(String gradleVersion, TestNGLifecycleType lifecycleType, String methodName) {
        GradleVersion.version(gradleVersion) > GRADLE_5_0
            ? "executionError"
            : CLASS_LIFECYCLE_METHODS.contains(lifecycleType) ? "classMethod" : "initializationError"
    }

    @Override
    String reportedParameterizedMethodName(String gradleVersion, String methodName, String paramType, int invocationNumber, @Nullable String paramValueRepresentation) {
        switch (GradleVersion.version(gradleVersion)) {
            case { it < GRADLE_6_1 }: return "${methodName}(${paramType})[${invocationNumber}]"
            case { it < GRADLE_7_0 }: return "[${invocationNumber}] ${paramValueRepresentation ?: ''}"
            case { (GRADLE_8_0 <= it && it < GRADLE_8_1) || it < GRADLE_7_6_4 }: return "${methodName}(${paramType})[${invocationNumber}]"
            default: return "${methodName}(${paramType}) > [${invocationNumber}] ${paramValueRepresentation ?: ''}"
        }
    }

    @Override
    boolean reportsSuccessfulLifecycleExecutions(TestNGLifecycleType lifecycleType) {
        !UNREPORTED_LIFECYCLE_METHODS.contains(lifecycleType)
    }

    def "retries all classes if failure occurs in #lifecycle (gradle version #gradleVersion)"(String gradleVersion, TestNGLifecycleType lifecycle) {
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
        def result = gradleRunner(gradleVersion as String).build()

        then:
        with(result.output) {
            // if BeforeTest fails, then methods won't be executed
            it.count('successTest SKIPPED') == (lifecycle == BEFORE_TEST ? 1 : 0)
            it.count('successTestWithLifecycle SKIPPED') == (lifecycle == BEFORE_TEST ? 1 : 0)

            it.count('successTest PASSED') == (lifecycle == BEFORE_TEST ? 1 : 2)
            it.count('successTestWithLifecycle PASSED') == (lifecycle == BEFORE_TEST ? 1 : 2)
            !it.contains("The following test methods could not be retried")
        }

        where:
        [gradleVersion, lifecycle] << GroovyCollections.combinations((Iterable) [
            GRADLE_VERSIONS_UNDER_TEST,
            UNREPORTED_LIFECYCLE_METHODS
        ])
    }
}
