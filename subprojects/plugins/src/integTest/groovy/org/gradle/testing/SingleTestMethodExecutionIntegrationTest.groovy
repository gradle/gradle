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

public class SingleTestMethodExecutionIntegrationTest extends AbstractIntegrationSpec {

    class TestFramework {
        String name
        String dependency
        String imports
        String toString() { name }
    }

    @Shared TestFramework jUnit = new TestFramework(name: "JUnit", dependency: "junit:junit:4.11", imports: "org.junit.*")
    @Shared TestFramework testNG = new TestFramework(name: "TestNG", dependency: "org.testng:testng:6.3.1", imports: "org.testng.annotations.*")

    @Unroll
    def "#framework executes single method from a test class"() {
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile "$framework.dependency" }
            test {
              use$framework.name()
              include 'FooTest*'
              selection {
                includeMethod('pass')
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
        result.testClass("FooTest").assertTestCount(1, 0, 0)

        where:
        framework << [jUnit, testNG]
    }

    @Unroll
    def "#framework executes multiple methods from a test class"() {
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile "$framework.dependency" }
            test {
              use$framework.name()
              include 'FooTest*'
              selection {
                includeMethod('passOne')
                includeMethod('passTwo')
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
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile "$framework.dependency" }
            test {
              use$framework.name()
              selection {
                includeMethod('pass')
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
        result.assertTestClassesExecuted("FooTest", "OtherTest")
        result.testClass("FooTest").assertTestCount(1, 0, 0)
        result.testClass("OtherTest").assertTestCount(1, 0, 0)

        where:
        framework << [jUnit, testNG]
    }
}