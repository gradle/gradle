/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.AbstractTestFilteringIntegrationTest
import org.gradle.testing.fixture.JUnitCoverage
import spock.lang.Issue

@TargetCoverage({ JUnitCoverage.LARGE_COVERAGE })
public class JUnitFilteringIntegrationTest extends AbstractTestFilteringIntegrationTest {

    void configureFramework() {
        framework = "JUnit"
        dependency = "junit:junit"
        imports = "org.junit.*"
    }

    void theParameterizedFiles() {
        file("src/test/java/ParameterizedFoo.java") << """import $imports;
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
            import org.junit.Test;
            public class FooTest {
                @Test
                public void testFoo() { }
            }
        """
        file("src/test/java/FooServerTest.java") << """
            import org.junit.Test;
            public class FooServerTest {
                @Test
                public void testFooServer() { }
            }
        """
        file("src/test/java/BarTest.java") << """
            import org.junit.Test;
            public class BarTest {
                @Test
                public void testBar() { }
            }
        """
        file("src/test/java/AllFooTests.java") << """
            import org.junit.runner.RunWith;
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
        run("test")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("ParameterizedFoo")
        result.testClass("ParameterizedFoo").assertTestsExecuted("pass[0]", "pass[1]", "pass[2]", "pass[3]", "pass[4]")
    }

    @Issue("GRADLE-3112")
    def "can filter parameterized tests from the command-line"() {
        given:
        theParameterizedFiles()

        when:
        run("test", "--tests", "*ParameterizedFoo.pass*")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("ParameterizedFoo")
        result.testClass("ParameterizedFoo").assertTestsExecuted("pass[0]", "pass[1]", "pass[2]", "pass[3]", "pass[4]")
    }

    @Issue("GRADLE-3112")
    def "passing a suite argument to --tests runs all tests in the suite"() {
        given:
        theSuiteFiles()

        when:
        run("test", "--tests", "*AllFooTests")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)

        result.assertTestClassesExecuted("FooTest", "FooServerTest")
        result.testClass("FooTest").assertTestCount(1, 0, 0);
        result.testClass("FooTest").assertTestsExecuted("testFoo")
        result.testClass("FooServerTest").assertTestCount(1, 0, 0);
        result.testClass("FooServerTest").assertTestsExecuted("testFooServer")
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
        run("test")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)

        result.assertTestClassesExecuted("FooTest", "FooServerTest")
        result.testClass("FooTest").assertTestCount(1, 0, 0);
        result.testClass("FooTest").assertTestsExecuted("testFoo")
        result.testClass("FooServerTest").assertTestCount(1, 0, 0);
        result.testClass("FooServerTest").assertTestsExecuted("testFooServer")
    }
}
