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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult

public class SingleTestMethodExecutionIntegrationTest extends AbstractIntegrationSpec {

    def "executes single method from a test class"() {
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.11' }
            test {
              include 'FooTest*'
              selection {
                includeMethod('pass')
              }
            }
        """
        file("src/test/java/FooTest.java") << """import org.junit.*;
            public class FooTest {
                @Test public void pass() {}
                @Test public void fail() { Assert.fail("Boo!"); }
            }
        """
        file("src/test/java/OtherTest.java") << """import org.junit.*;
            public class OtherTest {
                @Test public void pass() {}
                @Test public void fail() { Assert.fail("Boo!"); }
            }
        """

        when:
        run("test")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("FooTest")
        result.testClass("FooTest").assertTestCount(1, 0, 0)
    }

    def "executes multiple methods from a test class"() {
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.11' }
            test {
              include 'FooTest*'
              selection {
                includeMethod('passOne')
                includeMethod('passTwo')
              }
            }
        """
        file("src/test/java/FooTest.java") << """import org.junit.*;
            public class FooTest {
                @Test public void passOne() {}
                @Test public void passTwo() {}
                @Test public void fail() { Assert.fail("Boo!"); }
            }
        """
        file("src/test/java/OtherTest.java") << """import org.junit.*;
            public class OtherTest {
                @Test public void passOne() {}
                @Test public void fail() { Assert.fail("Boo!"); }
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
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.11' }
            test {
              selection {
                includeMethod('pass')
              }
            }
        """
        file("src/test/java/FooTest.java") << """import org.junit.*;
            public class FooTest {
                @Test public void pass() {}
                @Test public void fail() { Assert.fail("Boo!"); }
            }
        """
        file("src/test/java/OtherTest.java") << """import org.junit.*;
            public class OtherTest {
                @Test public void pass() {}
                @Test public void fail() { Assert.fail("Boo!"); }
            }
        """

        when:
        run("test")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("FooTest", "OtherTest")
        result.testClass("FooTest").assertTestCount(1, 0, 0)
        result.testClass("OtherTest").assertTestCount(1, 0, 0)
    }
}
