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

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import spock.lang.Issue

abstract class AbstractTestFilteringIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {

    def setup() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """
    }

    def "can run single tests"() {
        given:
        file('src/test/java/NotATest.java') << """
            public class NotATest {
            }
        """.stripIndent()
        file('src/test/java/Ok.java') << """
            ${testFrameworkImports}
            public class Ok {
                @Test
                public void ok() {
                }
            }
        """.stripIndent()
        file('src/test/java/Ok2.java') << """
            ${testFrameworkImports}
            public class Ok2 {
                @Test
                public void ok() {
                }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """.stripIndent()

        when:
        succeeds("test", "--tests=Ok2*")

        then:
        def testResult = new DefaultTestExecutionResult(testDirectory)
        testResult.assertTestClassesExecuted('Ok2')

        when:
        succeeds("cleanTest", "test", "--tests=Ok*")

        then:
        testResult.assertTestClassesExecuted('Ok', 'Ok2')

        when:
        fails("test", "--tests=DoesNotMatchAClass*")

        then:
        result.assertHasCause('No tests found for given includes: [DoesNotMatchAClass*](--tests filter)')

        when:
        fails("test", "--tests=NotATest*")
        then:
        result.assertHasCause('No tests found for given includes: [NotATest*](--tests filter)')
    }

    def "executes single method from a test class"() {
        buildFile << """
            test {
              filter {
                includeTestsMatching "${pattern}"
              }
            }
        """
        file("src/test/java/org/gradle/FooTest.java") << """
            package org.gradle;
            ${testFrameworkImports}
            public class FooTest {
                @Test public void pass() {}
                @Test public void fail() { throw new RuntimeException("Boo!"); }
            }
        """
        file("src/test/java/org/gradle/OtherTest.java") << """
            package org.gradle;
            ${testFrameworkImports}
            public class OtherTest {
                @Test public void pass() {}
                @Test public void fail() { throw new RuntimeException("Boo!"); }
            }
        """

        when:
        run("test")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("org.gradle.FooTest")
        result.testClass("org.gradle.FooTest").assertTestsExecuted("pass")

        where:
        pattern << ['FooTest.pass', 'org.gradle.FooTest.pass']
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
        file("src/test/java/FooTest.java") << """
            ${testFrameworkImports}
            public class FooTest {
                @Test public void passOne() {}
                @Test public void passTwo() {}
                @Test public void fail() { throw new RuntimeException("Boo!"); }
            }
        """
        file("src/test/java/OtherTest.java") << """
            ${testFrameworkImports}
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
        file("src/test/java/Foo1Test.java") << """
            ${testFrameworkImports}
            public class Foo1Test {
                @Test public void pass1() {}
                @Test public void boo() {}
            }
        """
        file("src/test/java/Foo2Test.java") << """
            ${testFrameworkImports}
            public class Foo2Test {
                @Test public void pass2() {}
                @Test public void boo() {}
            }
        """
        file("src/test/java/Foo3Test.java") << """
            ${testFrameworkImports}
            public class Foo3Test {
                @Test public void boo() {}
            }
        """
        file("src/test/java/OtherTest.java") << """
            ${testFrameworkImports}
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
        file("src/test/java/org/gradle/FooTest.java") << """
            package org.gradle;
            ${testFrameworkImports}
            public class FooTest {
                @Test public void pass() {}
            }
        """

        //by command line
        when: fails("test", "--tests", pattern)
        then: failure.assertHasCause("No tests found for given includes: [${pattern}](--tests filter)")

        //by build script
        when:
        buildFile << "test.filter.includeTestsMatching '${pattern}'"
        fails("test")
        then: failure.assertHasCause("No tests found for given includes: [${pattern}](filter.includeTestsMatching)")

        where:
        pattern << ['FooTest.missingMethod', 'org.gradle.FooTest.missingMethod']
    }

    def "adds import/export rules to report about no matching methods found"() {
        file("src/test/java/FooTest.java") << """
            ${testFrameworkImports}
            public class FooTest {
                @Test public void pass() {}
            }
        """

        when:
        buildFile << """
            test {
                include 'FooTest*'
                exclude 'NotImportant*'
            }
        """
        fails("test", "--tests", 'FooTest.missingMethod')
        then: failure.assertHasCause("No tests found for given includes: [FooTest*](include rules) [NotImportant*](exclude rules) [FooTest.missingMethod](--tests filter)")
    }

    def "does not report when matching method has been filtered before via include/exclude"() { //current behavior, not necessarily desired
        file("src/test/java/FooTest.java") << """
            ${testFrameworkImports}
            public class FooTest {
                @Test public void pass() {}
            }
        """

        when:
        buildFile << "test.include 'FooTest.missingMethod'"
        then:
        succeeds("test", "--tests", 'FooTest.missingMethod')
    }

    def "task is out of date when --tests argument changes"() {
        file("src/test/java/FooTest.java") << """
            ${testFrameworkImports}
            public class FooTest {
                @Test public void pass() {}
                @Test public void pass2() {}
            }
        """

        when: run("test", "--tests", "FooTest.pass")
        then: new DefaultTestExecutionResult(testDirectory).testClass("FooTest").assertTestsExecuted("pass")

        when: run("test", "--tests", "FooTest.pass")
        then: skipped(":test") //up-to-date

        when:
        run("test", "--tests", "FooTest.pass*")

        then:
        executedAndNotSkipped(":test")
        new DefaultTestExecutionResult(testDirectory).testClass("FooTest").assertTestsExecuted("pass", "pass2")
    }

    def "can select multiple tests from commandline #scenario"() {
        given:
        file("src/test/java/Foo1Test.java") << """
            ${testFrameworkImports}
            public class Foo1Test {
                @Test public void pass1() {}
                @Test public void bar() {}
            }
        """
        file("src/test/java/Foo2Test.java") << """
            ${testFrameworkImports}
            public class Foo2Test {
                @Test public void pass2() {}
                @Test public void bar() {}
            }
        """
        file("src/test/java/BarTest.java") << """
            ${testFrameworkImports}
            public class BarTest {
                @Test public void bar() {}
            }
        """
        file("src/test/java/OtherTest.java") << """
            ${testFrameworkImports}
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
        "pass and Other" | ["test", "--tests", "*.pass1", "--tests", "*OtherTest*"] | ["Foo1Test", "OtherTest"]                        | ["pass1"]         | []                | []               | ["bar", "pass3"]
        "pass and *ar"   | ["test", "--tests", "*.pass1", "--tests", "*arTest"]     | ["BarTest", "Foo1Test"]                          | ["pass1"]         | []                | ["bar"]          | []
    }

    @Issue("https://github.com/gradle/gradle/issues/1571")
    def "option --tests filter in combined with #includeType"() {
        given:
        buildFile << """
        test {
            $includeConfig
        }
        """

        when:
        createTestABC()

        then:
        succeeds('test', '--tests', '*ATest*', '--tests', '*BTest*', '--info')

        output.contains('ATest!')
        !output.contains('BTest!')
        !output.contains('CTest!')

        where:
        includeType                   | includeConfig
        "include and exclude"         | "include '*Test*'; exclude '*BTest*'"
        "filter.includeTestsMatching" | "filter { includeTestsMatching '*ATest*'; includeTestsMatching '*CTest*' }"
        "filter.includePatterns"      | "filter { includePatterns = ['*ATest*', '*CTest*'] }"
    }

    def "invoking testNameIncludePatterns does not influence include/exclude filter"() {
        given:
        buildFile << """
        test {
            include '*ATest*', '*BTest*'
            testNameIncludePatterns = [ '*BTest*', '*CTest*' ]
        }
        """

        when:
        createTestABC()

        then:
        succeeds('test', '--info')

        !output.contains('ATest!')
        output.contains('BTest!')
        !output.contains('CTest!')
    }

    def "invoking filter.includePatterns not disable include/exclude filter"() {
        given:
        buildFile << """
        test {
            include '*ATest*', '*BTest*'
            filter.includePatterns = [ '*BTest*', '*CTest*' ]
        }
        """

        when:
        createTestABC()

        then:
        succeeds('test', '--info')

        !output.contains('ATest!')
        output.contains('BTest!')
        !output.contains('CTest!')
    }

    def "can exclude tests"() {
        given:
        buildFile << """
        test {
            filter.excludeTestsMatching("*BTest.test*")
        }
        """

        createTestABC()

        when:
        succeeds('test', '--info')

        then:
        executedAndNotSkipped(":test")

        and:
        def executionResult = new DefaultTestExecutionResult(testDirectory)
        executionResult.testClass("ATest").assertTestsExecuted("test")
        !executionResult.testClassExists("BTest")
        executionResult.testClass("CTest").assertTestsExecuted("test")

        and:
        output.contains('ATest!')
        !output.contains('BTest!')
        output.contains('CTest!')
    }

    private createTestABC(){
        file('src/test/java/ATest.java') << """
            ${testFrameworkImports}
            public class ATest {
                @Test public void test() { System.out.println("ATest!"); }
            }
        """
        file('src/test/java/BTest.java') << """
            ${testFrameworkImports}
            public class BTest {
                @Test public void test() { System.out.println("BTest!"); }
            }
        """
        file('src/test/java/CTest.java') << """
            ${testFrameworkImports}
            public class CTest {
                @Test public void test() { System.out.println("CTest!"); }
            }
        """
    }

    private String[] stringArrayOf(List<String> strings) {
        return strings.toArray()
    }
}
