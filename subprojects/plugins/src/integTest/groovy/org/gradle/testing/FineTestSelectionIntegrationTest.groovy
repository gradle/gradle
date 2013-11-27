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
package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import spock.lang.Shared
import spock.lang.Unroll

public class FineTestSelectionIntegrationTest extends AbstractIntegrationSpec {

    class TestFramework {
        String name
        String imports
        String toString() { name }
    }

    @Shared TestFramework jUnit = new TestFramework(name: "JUnit", imports: "org.junit.*")
    @Shared TestFramework testNG = new TestFramework(name: "TestNG", imports: "org.testng.annotations.*")

    void setup() {
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile "org.testng:testng:6.3.1", "junit:junit:4.11" }
        """
    }

    @Unroll
    def "#framework executes single method from a test class"() {
        buildFile << """
            test {
              use$framework.name()
              selection {
                include {
                  name "FooTest.pass"
                }
              }
            }
        """
        file("src/test/java/FooTest.java") << """import $framework.imports;
            public class FooTest {
                @Test public void pass() {}
                @Test public void fail() { throw new RuntimeException("Boo!"); }
            }
        """
        file("src/test/java/OtherTest.java") << """import $framework.imports;
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

        where:
        framework << [jUnit, testNG]
    }

    @Unroll
    def "#framework executes multiple methods from a test class"() {
        buildFile << """
            test {
              use$framework.name()
              include 'FooTest*'
              def cls = "FooTest"
              selection.include {
                name "\${cls}.passOne" //make sure GStrings work
                name "\${cls}.passTwo"
              }
            }
        """
        file("src/test/java/FooTest.java") << """import $framework.imports;
            public class FooTest {
                @Test public void passOne() {}
                @Test public void passTwo() {}
                @Test public void fail() { throw new RuntimeException("Boo!"); }
            }
        """
        file("src/test/java/OtherTest.java") << """import $framework.imports;
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

        where:
        framework << [jUnit, testNG]
    }

    @Unroll
    def "#framework executes multiple methods from different classes"() {
        buildFile << """
            test {
              use$framework.name()
              selection.include.setNames 'Foo*.pass*'
            }
        """
        file("src/test/java/Foo1Test.java") << """import $framework.imports;
            public class Foo1Test {
                @Test public void pass1() {}
                @Test public void boo() {}
            }
        """
        file("src/test/java/Foo2Test.java") << """import $framework.imports;
            public class Foo2Test {
                @Test public void pass2() {}
                @Test public void boo() {}
            }
        """
        file("src/test/java/Foo3Test.java") << """import $framework.imports;
            public class Foo3Test {
                @Test public void boo() {}
            }
        """
        file("src/test/java/OtherTest.java") << """import $framework.imports;
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

        where:
        framework << [jUnit, testNG]
    }

    @Unroll
    def "#framework reports when no matching methods found"() {
        buildFile << """
            test {
              use$framework.name()
              selection.include.name 'FooTest.missingMethod'
            }
        """
        file("src/test/java/FooTest.java") << """import $framework.imports;
            public class FooTest {
                @Test public void pass() {}
            }
        """

        when:
        fails("test")

        then:
        failure.assertHasCause("No tests found for given includes: [FooTest.missingMethod]")

        where:
        framework << [jUnit, testNG]
    }

    @Unroll
    def "#framework task is out of date when included methods change"() {
        buildFile << """
            test {
              use$framework.name()
              selection.include.name 'FooTest.pass'
            }
        """
        file("src/test/java/FooTest.java") << """import $framework.imports;
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
        run("test", "--only", "FooTest.pass2,FooTest.pass")

        then:
        !result.skippedTasks.contains(":test")
        new DefaultTestExecutionResult(testDirectory).testClass("FooTest").assertTestsExecuted("pass", "pass2")

        where:
        framework << [jUnit, testNG]
    }
}