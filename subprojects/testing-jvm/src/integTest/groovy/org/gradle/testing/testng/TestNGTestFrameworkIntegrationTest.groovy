/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.testing.AbstractTestFrameworkIntegrationTest
import org.gradle.testing.fixture.TestNGCoverage
import spock.lang.Issue

class TestNGTestFrameworkIntegrationTest extends AbstractTestFrameworkIntegrationTest {
    def setup() {
        TestNGCoverage.enableTestNG(buildFile)
    }

    @Override
    void createPassingFailingTest() {
        file('src/test/java/AppException.java') << 'public class AppException extends Exception {}'
        file('src/test/java/SomeTest.java') << """
            public class SomeTest {
                @org.testng.annotations.Test
                public void ${failingTestCaseName}() { 
                    System.err.println("some error output");
                    assert false : "test failure message"; 
                }
                @org.testng.annotations.Test
                public void ${passingTestCaseName}() {}
            }
        """
        file('src/test/java/SomeOtherTest.java') << """
            public class SomeOtherTest {
                @org.testng.annotations.Test
                public void ${passingTestCaseName}() {}
            }
        """
    }

    @Override
    void createEmptyProject() {
        file("src/test/java/NotATest.java") << """
            public class NotATest {}
        """
    }

    @Override
    void renameTests() {
        def newTest = file("src/test/java/NewTest.java")
        file('src/test/java/SomeOtherTest.java').renameTo(newTest)
        newTest.text = newTest.text.replaceAll("SomeOtherTest", "NewTest")
    }

    @Override
    String getTestTaskName() {
        return "test"
    }

    @Override
    String getPassingTestCaseName() {
        return "pass"
    }

    @Override
    String getFailingTestCaseName() {
        return "fail"
    }

    @Issue("https://github.com/gradle/gradle/issues/3545")
    @ToBeFixedForInstantExecution
    def "can run tests with ignored test class"() {
        given:
        file("src/test/java/DisabledTest.java") << """
            @org.testng.annotations.Test(enabled = false)
            public class DisabledTest {
                public void testOne() {}
                public void testTwo() {}
            }
        """

        when:
        succeeds "test"

        then:
        testResult.assertNoTestClassesExecuted()
    }

    @Issue("https://github.com/gradle/gradle/issues/3545")
    @ToBeFixedForInstantExecution
    def "can run tests with ignored test methods"() {
        given:
        file("src/test/java/DisabledTest.java") << """
            public class DisabledTest {
                @org.testng.annotations.Test(enabled = false)
                public void testOne() {}
                
                @org.testng.annotations.Test(enabled = false)
                public void testTwo() {}
            }
        """

        when:
        succeeds "test"

        then:
        testResult.assertNoTestClassesExecuted()
    }
}
