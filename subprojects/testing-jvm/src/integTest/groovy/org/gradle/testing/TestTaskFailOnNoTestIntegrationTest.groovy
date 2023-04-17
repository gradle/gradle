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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TestTaskFailOnNoTestIntegrationTest extends AbstractIntegrationSpec {

    def "#task succeeds if there is a test"() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testImplementation 'org.testng:testng:6.3.1' }
            test { useTestNG() }
        """.stripIndent()

        file("src/test/java/SomeTest.java") << """
            public class SomeTest {
                @org.testng.annotations.Test
                public void foo() { }
            }
        """

        when:
        succeeds(task as String[])

        then:
        noExceptionThrown()

        where:
        task  << [["test"], ["test", "--no-fail-on-no-test"]]
    }

    def "test --fail-on-no-test fails if there is no test"() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testImplementation 'org.testng:testng:6.3.1' }
            test { useTestNG() }
        """.stripIndent()

        file("src/test/java/NotATest.java") << """
            public class NotATest {}
        """

        expect:
        def failure = fails("test", "--fail-on-no-test")
        failure.assertHasErrorOutput("No tests found for given includes: ")
    }

    def "test --no-fail-on-no-test succeeds if there is no test"() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testImplementation 'org.testng:testng:6.3.1' }
            test { useTestNG() }
        """.stripIndent()

        file("src/test/java/NotATest.java") << """
            public class NotATest {}
        """

        when:
        succeeds("test", "--no-fail-on-no-test")

        then:
        noExceptionThrown()
    }

    def "#task is skipped if there is no test file"() {
        buildFile << "apply plugin: 'java'"

        file("src/test/java/not_a_test.txt")

        when:
        succeeds(task as String[])

        then:
        skipped(":test")


        where:
        task  << [["test"], ["test", "--fail-on-no-test"], ["test", "--no-fail-on-no-test"]]
    }
}
