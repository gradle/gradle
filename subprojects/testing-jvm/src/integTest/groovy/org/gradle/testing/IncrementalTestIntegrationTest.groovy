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
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_4_LATEST
import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE_JUPITER

@TargetCoverage({ JUNIT_4_LATEST + JUNIT_VINTAGE_JUPITER })
class IncrementalTestIntegrationTest extends MultiVersionIntegrationSpec {

    @Rule public final TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        executer.noExtraLogging()
        executer.withRepositoryMirrors()
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
        succeeds('test').assertTasksNotSkipped(':compileJava', ':classes', ':test')
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

        result.assertTestClassesExecuted('TestNGTest') //previous result still present in the dir

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
            ${mavenCentralRepository()}
            dependencies { testImplementation '$testJunitCoordinates' }
            test.beforeTest { println "executed " + it }
        """

        when:
        succeeds("test", "--tests", "Foo*")

        then:
        //asserting on output because test results are kept in between invocations
        outputDoesNotContain("executed Test test(BarTest)")
        outputContains("executed Test test(FooTest)")

        when:
        succeeds("test", "--tests", "Bar*")

        then:
        outputContains("executed Test test(BarTest)")
        outputDoesNotContain("executed Test test(FooTest)")

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
