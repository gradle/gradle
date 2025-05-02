/*
 * Copyright 2011 the original author or authors.
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

import org.apache.commons.lang.RandomStringUtils
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest

/**
 * This class contains tests that don't fit well into other function-specific test classes.
 */
abstract class AbstractJUnitIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    def setup() {
        executer.noExtraLogging()
    }

    def "can use test super classes from another project"() {
        given:
        file('settings.gradle').write("include 'a', 'b'")
        file('b/build.gradle') << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${getTestFrameworkDependencies('main')}
            }
            test.${configureTestFramework}
        """.stripIndent()
        file('b/src/main/java/org/gradle/AbstractTest.java') << """
            package org.gradle;
            ${testFrameworkImports}
            public abstract class AbstractTest {
                @Test public void ok() { }
            }
        """.stripIndent()
        file('a/build.gradle') << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
                testImplementation project(':b')
            }
            test.${configureTestFramework}
        """.stripIndent()
        file('a/src/test/java/org/gradle/SomeTest.java') << '''
            package org.gradle;
            public class SomeTest extends AbstractTest {
            }
        '''.stripIndent()

        when:
        executer.withTasks('a:test').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory.file('a'))
        result.assertTestClassesExecuted('org.gradle.SomeTest')
        result.testClass('org.gradle.SomeTest').assertTestPassed('ok')
    }

    def "can exclude super classes from execution"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test {
                ${configureTestFramework}
                exclude '**/BaseTest.*'
            }
        """.stripIndent()
        file('src/test/java/org/gradle/BaseTest.java') << """
            package org.gradle;
            ${testFrameworkImports}
            public class BaseTest {
                @Test public void ok() { }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/SomeTest.java') << '''
            package org.gradle;
            public class SomeTest extends BaseTest {
            }
        '''.stripIndent()

        when:
        executer.withTasks('test').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SomeTest')
        result.testClass('org.gradle.SomeTest').assertTestPassed('ok')
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    def "can have multiple test task instances"() {
        given:
        file('src/test/java/org/gradle/Test1.java') << """
            package org.gradle;
            ${testFrameworkImports}
            public class Test1 {
                @Test public void ok() { }
            }
        """.stripIndent()
        file('src/test2/java/org/gradle/Test2.java') << """
            package org.gradle;
            ${testFrameworkImports}
            public class Test2 {
                @Test public void ok() { }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'

            ${mavenCentralRepository()}

            sourceSets {
                test2
            }

            test.${configureTestFramework}

            task test2(type: Test) {
                ${configureTestFramework}
                classpath = sourceSets.test2.runtimeClasspath
                testClassesDirs = sourceSets.test2.output.classesDirs
            }

            check {
                dependsOn test2
            }

            dependencies {
                ${getTestFrameworkDependencies('test')}
                ${getTestFrameworkDependencies('test2')}
            }
        """.stripIndent()

        when:
        executer.withTasks('check').run()

        then:
        def testResult = new JUnitXmlTestExecutionResult(testDirectory)
        testResult.assertTestClassesExecuted('org.gradle.Test1')
        testResult.testClass('org.gradle.Test1').assertTestPassed('ok')

        def test2Result = new JUnitXmlTestExecutionResult(testDirectory, 'build/test-results/test2')
        test2Result.assertTestClassesExecuted('org.gradle.Test2')
        test2Result.testClass('org.gradle.Test2').assertTestPassed('ok')
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "can use long paths for working directory"() {
        given:
        // windows can handle a path up to 260 characters
        // we create a path that is 260 +1 (offset + "/" + randompath)
        def pathoffset = 260 - testDirectory.getAbsolutePath().length()
        def alphanumeric = RandomStringUtils.randomAlphanumeric(pathoffset)
        def testWorkingDir = testDirectory.createDir("$alphanumeric")

        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
            test.workingDir = "${testWorkingDir.toURI()}"
        """.stripIndent()

        and:
        file("src/test/java/SomeTest.java") << """
            ${testFrameworkImports}

            public class SomeTest {
                @Test public void foo() {
                    System.out.println(new java.io.File(".").getAbsolutePath());
                }
            }
        """.stripIndent()

        expect:
        succeeds "test"
    }
}
