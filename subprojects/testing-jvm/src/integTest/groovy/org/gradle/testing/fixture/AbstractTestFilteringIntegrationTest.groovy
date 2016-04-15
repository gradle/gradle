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
package org.gradle.testing.fixture

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf
import spock.lang.Unroll

abstract class AbstractTestFilteringIntegrationTest extends MultiVersionIntegrationSpec {

    protected String framework
    protected String dependency
    protected String imports

    abstract void configureFramework()

    void setup() {
        configureFramework()
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile '$dependency:$org.gradle.integtests.fixtures.MultiVersionIntegrationSpec.version' }
            test { use${framework}() }
        """
    }

    def "executes single method from a test class"() {
        buildFile << """
            test {
              filter {
                includeTestsMatching "FooTest.pass"
              }
            }
        """
        file("src/test/java/FooTest.java") << """import $imports;
            public class FooTest {
                @Test public void pass() {}
                @Test public void fail() { throw new RuntimeException("Boo!"); }
            }
        """
        file("src/test/java/OtherTest.java") << """import $imports;
            public class OtherTest {
                @Test public void pass() {}
                @Test public void fail() { throw new RuntimeException("Boo!"); }
            }
        """

        when:
        run("test")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("FooTest")
        result.testClass("FooTest").assertTestsExecuted("pass")
    }

    def "executes multiple methods from a test class"() {
        buildFile << """
            test {
              include 'FooTest*'
              def cls = "FooTest"
              filter {
                includeTestsMatching "\${cls}.passOne" //make sure GStrings work
                includeTestsMatching "\${cls}.passTwo"
              }
            }
        """
        file("src/test/java/FooTest.java") << """import $imports;
            public class FooTest {
                @Test public void passOne() {}
                @Test public void passTwo() {}
                @Test public void fail() { throw new RuntimeException("Boo!"); }
            }
        """
        file("src/test/java/OtherTest.java") << """import $imports;
            public class OtherTest {
                @Test public void passOne() {}
                @Test public void fail() { throw new RuntimeException("Boo!"); }
            }
        """

        when:
        run("test")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("FooTest")
        result.testClass("FooTest").assertTestCount(2, 0, 0)
    }

    def "executes multiple methods from different classes"() {
        buildFile << """
            test {
              filter.setIncludePatterns 'Foo*.pass*'
            }
        """
        file("src/test/java/Foo1Test.java") << """import $imports;
            public class Foo1Test {
                @Test public void pass1() {}
                @Test public void boo() {}
            }
        """
        file("src/test/java/Foo2Test.java") << """import $imports;
            public class Foo2Test {
                @Test public void pass2() {}
                @Test public void boo() {}
            }
        """
        file("src/test/java/Foo3Test.java") << """import $imports;
            public class Foo3Test {
                @Test public void boo() {}
            }
        """
        file("src/test/java/OtherTest.java") << """import $imports;
            public class OtherTest {
                @Test public void pass3() {}
                @Test public void boo() {}
            }
        """

        when:
        run("test")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("Foo1Test", "Foo2Test")
        result.testClass("Foo1Test").assertTestsExecuted("pass1")
        result.testClass("Foo2Test").assertTestsExecuted("pass2")
    }

    def "reports when no matching methods found"() {
        file("src/test/java/FooTest.java") << """import $imports;
            public class FooTest {
                @Test public void pass() {}
            }
        """

        //by command line
        when: fails("test", "--tests", 'FooTest.missingMethod')
        then: failure.assertHasCause("No tests found for given includes: [FooTest.missingMethod]")

        //by build script
        when:
        buildFile << "test.filter.includeTestsMatching 'FooTest.missingMethod'"
        fails("test")
        then: failure.assertHasCause("No tests found for given includes: [FooTest.missingMethod]")
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "task is out of date when included methods change"() {
        buildFile << """
            test {
              filter.includeTestsMatching 'FooTest.pass'
            }
        """
        file("src/test/java/FooTest.java") << """import $imports;
            public class FooTest {
                @Test public void pass() {}
                @Test public void pass2() {}
            }
        """

        when: run("test")
        then: new DefaultTestExecutionResult(testDirectory).testClass("FooTest").assertTestsExecuted("pass")

        when: run("test")
        then: result.skippedTasks.contains(":test") //up-to-date

        when:
        run("test", "--tests", "FooTest.pass*")

        then:
        !result.skippedTasks.contains(":test")
        new DefaultTestExecutionResult(testDirectory).testClass("FooTest").assertTestsExecuted("pass", "pass2")
    }

    @Unroll
    def "can select multiple tests from commandline #scenario"() {
        given:
        file("src/test/java/Foo1Test.java") << """import $imports;
            public class Foo1Test {
                @Test public void pass1() {}
                @Test public void bar() {}
            }
        """
        file("src/test/java/Foo2Test.java") << """import $imports;
            public class Foo2Test {
                @Test public void pass2() {}
                @Test public void bar() {}
            }
        """
        file("src/test/java/BarTest.java") << """import $imports;
            public class BarTest {
                @Test public void bar() {}
            }
        """
        file("src/test/java/OtherTest.java") << """import $imports;
            public class OtherTest {
                @Test public void pass3() {}
                @Test public void bar() {}
            }
        """

        when:
        run(stringArrayOf(command))

        then:

        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted(stringArrayOf(classesExecuted))
        if (!foo1TestsExecuted.isEmpty()) {
            result.testClass("Foo1Test").assertTestsExecuted(stringArrayOf(foo1TestsExecuted))
        }
        if (!foo2TestsExecuted.isEmpty()) {
            result.testClass("Foo2Test").assertTestsExecuted(stringArrayOf(foo2TestsExecuted))
        }
        if (!barTestsExecuted.isEmpty()) {
            result.testClass("BarTest").assertTestsExecuted(stringArrayOf(barTestsExecuted))
        }
        if (!otherTestsExecuted.isEmpty()) {
            result.testClass("OtherTest").assertTestsExecuted(stringArrayOf(otherTestsExecuted))
        }

        where:
        scenario         | command                                                  | classesExecuted                                  | foo1TestsExecuted | foo2TestsExecuted | barTestsExecuted | otherTestsExecuted
        "no options"     | ["test"]                                                 | ["Foo1Test", "Foo2Test", "BarTest", "OtherTest"] | ["bar", "pass1"]  | ["bar", "pass2"]  | ["bar"]          | ["bar", "pass3"]
        "pass and Ohter" | ["test", "--tests", "*.pass1", "--tests", "*OtherTest*"] | ["Foo1Test", "OtherTest"]                        | ["pass1"]         | []                | []               | ["bar", "pass3"]
        "pass and *ar"   | ["test", "--tests", "*.pass1", "--tests", "*arTest"]     | ["BarTest", "Foo1Test"]                          | ["pass1"]         | []                | ["bar"]          | []
    }

    def String[] stringArrayOf(List<String> strings) {
        return strings.toArray()
    }
}
