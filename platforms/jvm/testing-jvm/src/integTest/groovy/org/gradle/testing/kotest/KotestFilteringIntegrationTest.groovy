/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.testing.kotest

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.JdkVersionTestPreconditions

/**
 * Filtering of Kotest tests. Kotest registers individual tests only while executing a spec, so Gradle's
 * post-discovery filters see nothing but the spec classes and can filter at class level only.
 */
@Requires(
    value = JdkVersionTestPreconditions.KotlinSupportedJdk,
    reason = "Kotest requires a JDK that supports Kotlin"
)
class KotestFilteringIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
    def setup() {
        buildFile << """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "${new KotlinGradlePluginVersions().latest}"
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                useJUnitJupiter()

                dependencies {
                    implementation("io.kotest:kotest-runner-junit5:5.9.1")
                }
            }
        """
        file('src/test/kotlin/org/example/SimpleKotestTest.kt') << """
            package org.example

            import io.kotest.core.spec.style.FunSpec

            class SimpleKotestTest : FunSpec({
                test("should add two numbers") {}
                test("should subtract two numbers") {}
            })
        """
        file('src/test/kotlin/org/example/OtherKotestTest.kt') << """
            package org.example

            import io.kotest.core.spec.style.FunSpec

            class OtherKotestTest : FunSpec({
                test("should print") {}
            })
        """
        file('src/test/java/org/example/JupiterTest.java') << """
            package org.example;

            import org.junit.jupiter.api.Test;

            public class JupiterTest {
                @Test
                public void someMethod() {}

                @Test
                public void otherMethod() {}
            }
        """
    }

    def "runs only tests matching command line filter #filter"() {
        when:
        succeeds("test", "--tests", filter)

        then:
        resultsFor().assertTestPathsExecuted(*expectedTestPaths)

        where:
        filter                   | expectedTestPaths
        'SimpleKotestTest'       | [':org.example.SimpleKotestTest:should add two numbers', ':org.example.SimpleKotestTest:should subtract two numbers']
        'org.example.Other*'     | [':org.example.OtherKotestTest:should print']
        'JupiterTest.someMethod' | [':org.example.JupiterTest:someMethod()']
        '*someMethod'            | [':org.example.JupiterTest:someMethod()']
    }

    def "excludes specs matching configured filter"() {
        given:
        buildFile << """
            test {
                filter {
                    excludeTestsMatching "SimpleKotestTest"
                }
            }
        """

        when:
        succeeds("test")

        then:
        resultsFor().assertTestPathsExecuted(
            ':org.example.OtherKotestTest:should print',
            ':org.example.JupiterTest:someMethod()',
            ':org.example.JupiterTest:otherMethod()'
        )
    }

    /**
     * Documents the status quo: a filter naming an individual Kotest test matches nothing, because the test
     * does not exist when the filter is applied. If this changes, this test should be replaced.
     */
    def "cannot select individual tests by name"() {
        when:
        fails("test", "--tests", "SimpleKotestTest.should add two numbers")

        then:
        failure.assertHasCause("No tests found for given includes: [SimpleKotestTest.should add two numbers](--tests filter)")
    }

    /**
     * Documents the status quo: a filter excluding an individual Kotest test by name has no effect, because the test
     * does not exist when the filter is applied. If this changes, this test should be replaced.
     */
    def "cannot exclude individual tests by name"() {
        given:
        buildFile << """
            test {
                filter {
                    excludeTestsMatching "*should add two numbers"
                }
            }
        """

        when:
        succeeds("test")

        then:
        resultsFor().assertTestPathsExecuted(
            ':org.example.SimpleKotestTest:should add two numbers',
            ':org.example.SimpleKotestTest:should subtract two numbers',
            ':org.example.OtherKotestTest:should print',
            ':org.example.JupiterTest:someMethod()',
            ':org.example.JupiterTest:otherMethod()'
        )
    }
}
