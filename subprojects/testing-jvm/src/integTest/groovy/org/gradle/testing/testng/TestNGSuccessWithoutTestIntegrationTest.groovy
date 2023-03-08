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

package org.gradle.testing.testng

import org.gradle.api.tasks.testing.TestExecutionException
import org.gradle.integtests.fixtures.executer.UnexpectedBuildFailure

class TestNGSuccessWithoutTestIntegrationTest extends TestNGTestFrameworkIntegrationTest {

    def "test source and test task use same test framework"() {
        given:
        buildFile << """
            apply plugin:'java-library'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.testng:testng:6.3.1'
            }
            test {
                useTestNG()
            }
        """

        file("src/test/java/NotATest.java") << """
            @org.testng.annotations.Test
            public class NotATest {}
        """

        when:
        executer.expectDocumentedDeprecationWarning("There is no test to run. This behavior has been deprecated. " +
            "This will fail with an error in Gradle 9.0. Set Test.successWithoutTest to true if you want the task to succeed when there is no test to run. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#test_task_success_without_test")
        run('test')

        then:
        noExceptionThrown()

        when:
        run('test', '--success-without-test')

        then:
        noExceptionThrown()

        when:
        run('test', '--no-success-without-test')

        then:
        UnexpectedBuildFailure buildFailure = thrown(UnexpectedBuildFailure)
        Throwable exception = buildFailure.cause.cause.cause
        exception.class.is(TestExecutionException.class)
        exception.message.startsWith("No tests found for given includes: ")
    }

    def "test source and test task use different test frameworks"() {
        given:
        buildFile << """
            apply plugin:'java-library'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:5.7.1'
            }
            test {
                useJUnitPlatform()
            }
        """

        createPassingFailingTest()

        when:
        executer.expectDocumentedDeprecationWarning("There is no test to run. This behavior has been deprecated. " +
            "This will fail with an error in Gradle 9.0. Set Test.successWithoutTest to true if you want the task to succeed when there is no test to run. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#test_task_success_without_test")
        run("test")

        then:
        noExceptionThrown()

        when:
        run('test', '--success-without-test')

        then:
        noExceptionThrown()

        when:
        run('test', '--no-success-without-test')

        then:
        UnexpectedBuildFailure buildFailure = thrown(UnexpectedBuildFailure)
        Throwable exception = buildFailure.cause.cause.cause
        exception.class.is(TestExecutionException.class)
        exception.message.startsWith("No tests found for given includes: ")
    }
}
