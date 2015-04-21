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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

class IncrementalTestIntegrationTest extends AbstractIntegrationSpec {

    @Rule public final TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        executer.noExtraLogging()
    }

    def doesNotRunStaleTests() {
        given:
        fails('test').assertTestsFailed()

        file('src/test/java/Broken.java').assertIsFile().delete()

        expect:
        succeeds('test')
    }

    def executesTestsWhenSourceChanges() {
        given:
        succeeds('test')

        when:
        // Change a production class
        file('src/main/java/MainClass.java').assertIsFile().copyFrom(file('NewMainClass.java'))

        then:
        succeeds('test').assertTasksNotSkipped(':compileJava', ':classes', ':compileTestJava', ':testClasses', ':test')
        succeeds('test').assertTasksNotSkipped()

        when:
        // Change a test class
        file('src/test/java/Ok.java').assertIsFile().copyFrom(file('NewOk.java'))

        then:
        succeeds('test').assertTasksNotSkipped(':compileTestJava', ':testClasses', ':test')
        succeeds('test').assertTasksNotSkipped()
    }

    def executesTestsWhenTestFrameworkChanges() {
        given:
        succeeds('test')

        def result = new JUnitXmlTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('JUnitTest')

        when:
        // Switch test framework
        file('build.gradle').append 'test.useTestNG()\n'

        then:
        succeeds('test').assertTasksNotSkipped(':test')

        result.assertTestClassesExecuted('TestNGTest', 'JUnitTest') //previous result still present in the dir

        succeeds('test').assertTasksNotSkipped()
    }

    def "test up-to-date status respects test name patterns"() {
        file("src/test/java/FooTest.java") << """
import org.junit.*;
public class FooTest {
    @Test public void test() {}
}
"""
        file("src/test/java/BarTest.java") << """
import org.junit.*;
public class BarTest {
    @Test public void test() {}
}
"""

        file("build.gradle") << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }
            test.beforeTest { println "executed " + it }
        """

        when:
        def result = executer.withTasks("test", "-Dtest.single=Foo").run()

        then:
        //asserting on output because test results are kept in between invocations
        !result.output.contains("executed Test test(BarTest)")
        result.output.contains("executed Test test(FooTest)")

        when:
        result = executer.withTasks("test", "-Dtest.single=Bar").run()

        then:
        result.output.contains("executed Test test(BarTest)")
        !result.output.contains("executed Test test(FooTest)")

        when:
        result = executer.withTasks("test", "-Dtest.single=Bar").run()

        then:
        result.assertTaskSkipped(":test")
    }
}
