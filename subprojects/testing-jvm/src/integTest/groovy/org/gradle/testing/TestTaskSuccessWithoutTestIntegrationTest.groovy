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

package org.gradle.testing

import org.gradle.api.tasks.testing.TestExecutionException
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.UnexpectedBuildFailure

class TestTaskSuccessWithoutTestIntegrationTest extends AbstractIntegrationSpec {

    def "test succeeds with warning if there is no test"() {
        buildFile << "apply plugin: 'java'"

        file("src/test/java/NotATest.java") << """
            public class NotATest {}
        """

        when:
        executer.expectDocumentedDeprecationWarning("There is no test to run. This behavior has been deprecated. " +
            "This will fail with an error in Gradle 9.0. Set Test.successWithoutTest to true if you want the task to succeed when there is no test to run. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#test_task_success_without_test")
        succeeds("test")

        then:
        noExceptionThrown()
    }

    def "test --success-without-test succeeds without warning if there is no test"() {
        buildFile << "apply plugin: 'java'"

        file("src/test/java/NotATest.java") << """
            public class NotATest {}
        """

        when:
        succeeds("test", "--success-without-test")

        then:
        noExceptionThrown()
    }

    def "test --no-success-without-test fails if there is no test"() {
        buildFile << "apply plugin: 'java'"

        file("src/test/java/NotATest.java") << """
            public class NotATest {}
        """

        when:
        run("test", "--no-success-without-test")

        then:
        UnexpectedBuildFailure buildFailure = thrown(UnexpectedBuildFailure)
        Throwable exception = buildFailure.cause.cause.cause
        exception.class.is(TestExecutionException.class)
        exception.message.startsWith("No tests found for given includes: ")
    }

    def "test is skipped if there is no test file"() {
        buildFile << "apply plugin: 'java'"

        file("src/test/java/not_a_test.txt")

        when:
        succeeds("test")

        then:
        skipped(":test")
    }

    def "test --success-without-test is skipped if there is no test file"() {
        buildFile << "apply plugin: 'java'"

        file("src/test/java/not_a_test.txt")

        when:
        succeeds("test", "--success-without-test")

        then:
        skipped(":test")
    }

    def "test --no-success-without-test is skipped if there is no test file"() {
        buildFile << "apply plugin: 'java'"

        file("src/test/java/not_a_test.txt")

        when:
        succeeds("test", "--no-success-without-test")

        then:
        skipped(":test")
    }
}
