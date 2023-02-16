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

class TestTaskNoTestsFailTest extends AbstractIntegrationSpec {

// This doesn't work with the @SkipWhenEmpty annotation on Test::getCandidateClassFiles

//    def "test --failIfNoTest fails if there is no test file"() {
//        buildFile << "apply plugin: 'java'"
//
//        file("src/test/java/not_a_test.txt")
//
//        when:
//        run("test", "--failIfNoTest")
//
//        then:
//        UnexpectedBuildFailure buildFailure = thrown(UnexpectedBuildFailure)
//        Throwable exception = buildFailure.cause.cause.cause
//        exception.class.is(TestExecutionException.class)
//        exception.message.startsWith("No tests found for given includes: ")
//    }

    def "test --failIfNoTest fails if there is no test"() {
        buildFile << "apply plugin: 'java'"

        file("src/test/java/NotATest.java") << """
            public class NotATest {}
        """

        when:
        run("test", "--failIfNoTest")

        then:
        UnexpectedBuildFailure buildFailure = thrown(UnexpectedBuildFailure)
        Throwable exception = buildFailure.cause.cause.cause
        exception.class.is(TestExecutionException.class)
        exception.message.startsWith("No tests found for given includes: ")
    }

    def "test succeeds if there is no test file"() {
        buildFile << "apply plugin: 'java'"

        file("src/test/java/not_a_test.txt")

        when:
        executer.expectDeprecationWarning("There is no test to run. You can use 'test --failIfNoTest' to fail in this case.")
        succeeds("test")

        then:
        noExceptionThrown()
    }

    def "test succeeds if there is no test"() {
        buildFile << "apply plugin: 'java'"

        file("src/test/java/NotATest.java") << """
            public class NotATest {}
        """

        when:
        executer.expectDeprecationWarning("There is no test to run. You can use 'test --failIfNoTest' to fail in this case.")
        succeeds("test")

        then:
        noExceptionThrown()
    }
}
