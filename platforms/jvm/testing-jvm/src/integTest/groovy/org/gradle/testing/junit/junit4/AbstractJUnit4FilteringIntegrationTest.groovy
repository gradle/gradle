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


package org.gradle.testing.junit.junit4

import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.api.tasks.testing.TestResult
import org.gradle.testing.AbstractTestFilteringIntegrationTest
import spock.lang.Issue

abstract class AbstractJUnit4FilteringIntegrationTest extends AbstractTestFilteringIntegrationTest {
    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.JUNIT4
    }

    void theParameterizedFiles() {
        file("src/test/java/ParameterizedFoo.java") << """
            ${testFrameworkImports}
            import org.junit.runners.Parameterized;
            import org.junit.runners.Parameterized.Parameters;
            import org.junit.runner.RunWith;
            import java.util.Arrays;
            import java.util.Collection;

            @RunWith(Parameterized.class)
            public class ParameterizedFoo {
                int index;
                public ParameterizedFoo(int index){
                    this.index = index;
                }

                @Parameters
                public static Collection data() {
                   return Arrays.asList(new Object[][] {
                      { 2 },
                      { 6 },
                      { 19 },
                      { 22 },
                      { 23 }
                   });
                }
                @Test public void pass() {}
                @Test public void fail() {}
            }
        """
    }

    void theSuiteFiles() {
        file("src/test/java/FooTest.java") << """
            ${testFrameworkImports}
            public class FooTest {
                @Test
                public void testFoo() { }
            }
        """
        file("src/test/java/FooServerTest.java") << """
            ${testFrameworkImports}
            public class FooServerTest {
                @Test
                public void testFooServer() { }
            }
        """
        file("src/test/java/BarTest.java") << """
            ${testFrameworkImports}
            public class BarTest {
                @Test
                public void testBar() { }
            }
        """
        file("src/test/java/AllFooTests.java") << """
            ${testFrameworkImports}
            import org.junit.runners.Suite;
            import org.junit.runners.Suite.SuiteClasses;
            @RunWith(Suite.class)
            @SuiteClasses({FooTest.class, FooServerTest.class})
            public class AllFooTests {
            }
        """
    }

    @Issue("GRADLE-3112")
    def "can filter parameterized tests from the build file."() {
        given:
        // this addition to the build file ...
        buildFile << """
            test {
              filter {
                includeTestsMatching "*ParameterizedFoo.pass*"
              }
            }
        """
        // and ...
        theParameterizedFiles()

        when:
        succeedsWithTestTaskArguments("test")

        then:
        verifyParameterizedTestResults()
    }

    @Issue("GRADLE-3112")
    def "can filter parameterized tests from the command-line"() {
        given:
        theParameterizedFiles()

        when:
        succeedsWithTestTaskArguments("test", "--tests", "*ParameterizedFoo.pass*")

        then:
        verifyParameterizedTestResults()
    }

    void verifyParameterizedTestResults() {
        GenericTestExecutionResult testResult = resultsFor("tests/test", testFramework)
        def fooResults = testResult.testPath("ParameterizedFoo", "").onlyRoot()
        if (passedTestOutcome == TestResult.ResultType.SUCCESS) {
            fooResults.assertOnlyChildrenExecuted("[0]", "[1]", "[2]", "[3]", "[4]")
        } else {
            fooResults.assertOnlyChildrenExecuted()
            fooResults.assertChildrenSkipped("[0]", "[1]", "[2]", "[3]", "[4]")
        }
        for (int i = 0; i < 5; i++) {
            testResult.testPath(":ParameterizedFoo:[$i]").onlyRoot().assertChildCount(1, 0)
            testResult.testPath(":ParameterizedFoo:[$i]:pass[$i]").onlyRoot().assertHasResult(passedTestOutcome)
        }
    }

    @Issue("GRADLE-3112")
    def "passing a suite argument to --tests runs all tests in the suite"() {
        given:
        theSuiteFiles()

        when:
        succeedsWithTestTaskArguments("test", "--tests", "*AllFooTests")

        then:
        GenericTestExecutionResult testResult = resultsFor("tests/test", testFramework)
        testResult.assertTestPathsExecuted(":AllFooTests:FooTest:testFoo", ":AllFooTests:FooServerTest:testFooServer")
        testResult.testPath(":AllFooTests:FooTest:testFoo").onlyRoot().assertHasResult(passedTestOutcome)
        testResult.testPath(":AllFooTests:FooServerTest:testFooServer").onlyRoot().assertHasResult(passedTestOutcome)
    }

    @Issue("GRADLE-3112")
    def "can filter test Suites from build file."() {
        given:
        // this addition to the build files ...
        buildFile << """
            test {
              filter {
                includeTestsMatching "*AllFooTests"
              }
            }
        """
        // and ...
        theSuiteFiles()

        when:
        succeedsWithTestTaskArguments("test")

        then:
        GenericTestExecutionResult testResult = resultsFor("tests/test", testFramework)
        testResult.assertTestPathsExecuted(":AllFooTests:FooTest:testFoo", ":AllFooTests:FooServerTest:testFooServer")
        testResult.testPath(":AllFooTests:FooTest:testFoo").onlyRoot().assertHasResult(passedTestOutcome)
        testResult.testPath(":AllFooTests:FooServerTest:testFooServer").onlyRoot().assertHasResult(passedTestOutcome)
    }
}
