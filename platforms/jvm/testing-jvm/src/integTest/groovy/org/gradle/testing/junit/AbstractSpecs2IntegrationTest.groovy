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

package org.gradle.testing.junit

import org.gradle.api.tasks.testing.TestResult
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.JdkVersionTestPreconditions

import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import spock.lang.Issue

abstract class AbstractSpecs2IntegrationTest extends AbstractTestingMultiVersionIntegrationTest {

    @Requires(value = JdkVersionTestPreconditions.Jdk23OrEarlier, reason = "2.11.12 is required for specs2 3.x, which is not compatible with running on JDK 24.")
    def 'can run Specs2 tests'() {
        given:
        buildFile << """
            plugins {
                id("scala")
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation 'org.scala-lang:scala-library:2.11.12'
                testImplementation 'org.specs2:specs2_2.11:3.7'
                testImplementation 'org.specs2:specs2-junit_2.11:4.7.0'
                ${testFrameworkDependencies}
            }
        """
        file('src/test/scala/BasicSpec.scala') << '''
            import org.junit.runner.RunWith
            import org.specs2.runner.JUnitRunner
            import org.specs2.mutable.Specification

            @RunWith(classOf[JUnitRunner])
            class BasicSpec extends Specification {
              "Basic Math" >> {
                (1 + 1) mustEqual 2
              }
            }
        '''

        when:
        succeeds('test')

        then:
        def results = resultsFor(testDirectory)
        results.testPath("BasicSpec").onlyRoot().assertChildCount(1, 0)
        results.testPath("BasicSpec", "Basic Math").onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
    }

    @Issue("https://github.com/gradle/gradle/issues/38193")
    def 'a Specs2 spec whose beforeAll() throws reports a test failure rather than an internal reporter error'() {
        given:
        buildFile << """
            plugins {
                id("scala")
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'org.scala-lang:scala-library:2.13.14'
                testImplementation 'org.specs2:specs2-core_2.13:4.20.9'
                testImplementation 'org.specs2:specs2-junit_2.13:4.20.9'
                testImplementation 'junit:junit:4.13.2'
                ${testFrameworkDependencies}
            }

            tasks.named('test', Test) {
                ${configureTestFramework}
            }
        """
        file('src/test/scala/com/example/FailureCaseSpec.scala') << '''
            package com.example

            import org.junit.runner.RunWith
            import org.specs2.mutable.Specification
            import org.specs2.runner.JUnitRunner
            import org.specs2.specification.BeforeAfterAll

            @RunWith(classOf[JUnitRunner])
            class FailureCaseSpec extends Specification with BeforeAfterAll {
              def beforeAll(): Unit = {
                sys.error("class-level setup failure (BeforeAfterAll)")
              }
              def afterAll(): Unit = ()

              "ZipDownloadSpec" should {
                "/zip_get_progress should" should {
                  "report progress for a valid request" in {
                    ok
                  }
                  "fail when the assertion does not hold" in {
                    1 must_=== 2
                  }
                }
              }
            }
        '''
        file('src/test/scala/com/example/SuccessfulCaseSpec.scala') << '''
            package com.example

            import org.junit.runner.RunWith
            import org.specs2.mutable.Specification
            import org.specs2.runner.JUnitRunner

            @RunWith(classOf[JUnitRunner])
            class SuccessfulCaseSpec extends Specification {

              "flat unit spec" should {
                "fail with a normal assertion error" in {
                  1 must_=== 2
                }
              }
            }
        '''

        when:
        executer.withStackTraceChecksDisabled()
        fails('test')

        then: "the build fails as a normal test failure, not as an internal test-reporter ClassCastException"
        errorOutput.contains("There were failing tests")
        !errorOutput.contains("No reporter found for test descriptor")
        !errorOutput.contains("GroupTestEventReporterInternal")
        !errorOutput.contains("Test process encountered an unexpected problem")

        and: "the post-fix tree shape matches the Gradle 9.2.1 expected output — single hierarchy, no duplicated synthetic suite"
        // The count is one pin: a duplicated tree (e.g., the registered class node
        // plus a synthetic suite for the same class) would shift the total to 5+.
        // The variant-specific test-line shape (JUnit 4 reports the synthetic test
        // as "classMethod"; JUnit Vintage reports it under the fully-qualified
        // class name) is not pinned here.
        errorOutput.contains("4 tests completed, 2 failed, 2 skipped")

        and: "the HTML report records exactly one failed direct child under FailureCaseSpec — catches silent suppression of the classMethod (or vintage-equivalent) event that the count pin alone would miss"
        // Uses VerifiesGenericTestReportResults (mixed in via AbstractTestingMultiVersionIntegrationTest).
        // The count getter is variant-independent — the child's name differs between
        // JUnit 4 ("classMethod") and JUnit Vintage (the fully-qualified class name),
        // but in both cases FailureCaseSpec has exactly one failed direct child.
        // The path is the fully-qualified class name because the spec lives in
        // package com.example.
        def reportResults = resultsFor(testDirectory)
        reportResults.testPath("com.example.FailureCaseSpec").onlyRoot().getFailedChildCount() == 1
    }
}
