/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest

abstract class AbstractIncrementalTestIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    def setup() {
        executer.noExtraLogging()
        executer.withRepositoryMirrors()
        buildFile << """
            apply plugin: 'java'

            ${mavenCentralRepository()}

            dependencies {
                ${testFrameworkDependencies}
            }

            test.${configureTestFramework}
        """.stripIndent()
        file('src/test/java/Ok.java') << """
            ${testFrameworkImports}
            public class Ok {
                @Test
                public void ok() {
                }
            }
        """.stripIndent()
    }

    def doesNotRunStaleTests() {
        given:
        file('src/test/java/Broken.java') << """
            ${testFrameworkImports}
            public class Broken {
                @Test
                public void broken() {
                    throw new RuntimeException("broken");
                }
            }
        """.stripIndent()

        when:
        fails('test').assertTestsFailed()

        file('src/test/java/Broken.java').assertIsFile().delete()

        then:
        succeeds('test')
    }

    def executesTestsWhenSourceChanges() {
        given:
        file('src/main/java/MainClass.java') << """
            class MainClass { }
        """.stripIndent()
        succeeds('test')

        when:
        // Change a production class
        file('src/main/java/MainClass.java').text = """
            public class MainClass {
                @Override
                public String toString() {
                    return "new main class";
                }
            }
        """.stripIndent()

        then:
        succeeds('test').assertTasksNotSkipped(':compileJava', ':classes', ':test')
        succeeds('test').assertTasksNotSkipped()

        when:
        // Change a test class
        file('src/test/java/Ok.java').text = """
            ${testFrameworkImports}
            public class Ok {
                @Test
                public void someTest() {
                }
            }
        """.stripIndent()

        then:
        succeeds('test').assertTasksNotSkipped(':compileTestJava', ':testClasses', ':test')
        succeeds('test').assertTasksNotSkipped()
    }

    def executesTestsWhenTestFrameworkChanges() {
        given:
        file('src/test/java/JUnitExtra.java') << """
            ${testFrameworkImports}
            public class JUnitExtra {
                @Test
                public void ok() {
                }
            }
        """.stripIndent()
        file('src/test/java/JUnitTest.java') << """
            ${testFrameworkImports}
            public class JUnitTest {
                @Test
                public void ok() {
                }
            }
        """.stripIndent()
        file('src/test/java/TestNGTest.java') << """
            import org.testng.annotations.*;
            public class TestNGTest {
                @Test
                public void ok() {
                }
            }
        """.stripIndent()
        buildFile.text = """
            apply plugin: 'java'

            ${mavenCentralRepository()}

            dependencies {
                ${testFrameworkDependencies}
                testImplementation 'org.testng:testng:6.3.1'
            }

            test {
                ${configureTestFramework}
                include '**/*Test.*'
            }
        """.stripIndent()
        succeeds('test')

        def result = new JUnitXmlTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('JUnitTest')

        when:
        // Switch test framework
        file('build.gradle').append 'test.useTestNG()\n'

        then:
        succeeds('test').assertTasksNotSkipped(':test')

        result.assertTestClassesExecuted('TestNGTest') //previous result still present in the dir

        succeeds('test').assertTasksNotSkipped()
    }

    def "test up-to-date status respects test name patterns"() {
        file("src/test/java/FooTest.java") << """
            ${testFrameworkImports}
            public class FooTest {
                @Test public void test() {}
            }
        """.stripIndent()
        file("src/test/java/BarTest.java") << """
            ${testFrameworkImports}
            public class BarTest {
                @Test public void test() {}
            }
        """.stripIndent()

        buildFile << """
            test.beforeTest { println "executed " + it }
        """

        when:
        succeeds("test", "--tests", "Foo*")

        then:
        //asserting on output because test results are kept in between invocations
        outputDoesNotContain("executed Test ${maybeParentheses('test')}(BarTest)")
        outputContains("executed Test ${maybeParentheses('test')}(FooTest)")

        when:
        succeeds("test", "--tests", "Bar*")

        then:
        outputContains("executed Test ${maybeParentheses('test')}(BarTest)")
        outputDoesNotContain("executed Test ${maybeParentheses('test')}(FooTest)")

        when:
        succeeds("test", "--tests", "Bar*")

        then:
        result.assertTaskSkipped(":test")
    }

    def "does not re-run tests when parameter of disabled report changes"() {
        buildFile << """
            test {
                reports.html {
                    required = true
                }
                reports.junitXml {
                    required = false
                    outputPerTestCase = Boolean.parseBoolean(project.property('outputPerTestCase'))
                }
            }
        """

        when:
        succeeds("test", "-PoutputPerTestCase=true")
        then:
        executedAndNotSkipped(":test")

        when:
        succeeds("test", "-PoutputPerTestCase=false")
        then:
        skipped(":test")
    }
}
