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

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testing.fixture.JUnitMultiVersionIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_4_LATEST
import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE_JUPITER

@TargetCoverage({ JUNIT_4_LATEST + JUNIT_VINTAGE_JUPITER })
class TestTaskIntegrationTest extends JUnitMultiVersionIntegrationSpec {

    @Issue("GRADLE-2702")
    @ToBeFixedForInstantExecution
    def "should not resolve configuration results when there are no tests"() {
        buildFile << """
            apply plugin: 'java'

            configurations.all { incoming.beforeResolve { throw new RuntimeException() } }
        """

        when:
        run("build")

        then:
        noExceptionThrown()
    }

    def "test task is skipped when there are no tests"() {
        buildFile << "apply plugin: 'java'"
        file("src/test/java/not_a_test.txt")

        when:
        run("build")

        then:
        result.assertTaskSkipped(":test")
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "compiles and executes a Java 9 test suite"() {
        given:
        buildFile << java9Build()

        file('src/test/java/MyTest.java') << standaloneTestClass()

        when:
        succeeds 'test'

        then:
        noExceptionThrown()

        and:
        classFormat(classFile('java', 'test', 'MyTest.class')) == 53

    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "compiles and executes a Java 9 test suite even if a module descriptor is on classpath"() {
        given:
        buildFile << java9Build()

        file('src/test/java/MyTest.java') << standaloneTestClass()
        file('src/main/java/com/acme/Foo.java') << '''package com.acme;
            public class Foo {}
        '''
        file('src/main/java/com/acme/module-info.java') << '''module com.acme {
            exports com.acme;
        }'''

        when:
        succeeds 'test'

        then:
        noExceptionThrown()

        and:
        classFormat(javaClassFile('module-info.class')) == 53
        classFormat(classFile('java', 'test', 'MyTest.class')) == 53
    }

    @Unroll
    def "test task does not hang if maxParallelForks is greater than max-workers (#maxWorkers)"() {
        given:
        def maxParallelForks = maxWorkers + 1

        and:
        2000.times { num ->
            file("src/test/java/SomeTest${num}.java") << testClass("SomeTest${num}")
        }

        and:
        buildFile << """
            apply plugin: 'java'
            ${jcenterRepository()}
            dependencies { testImplementation 'junit:junit:4.12' }
            test {
                maxParallelForks = $maxParallelForks
            }
        """.stripIndent()

        when:
        executer.withArguments("--max-workers=${maxWorkers}", "-i")
        succeeds 'test'

        then:
        output.contains("test.maxParallelForks ($maxParallelForks) is larger than max-workers ($maxWorkers), forcing it to $maxWorkers")

        where:
        maxWorkers                                | _
        Runtime.runtime.availableProcessors()     | _
        Runtime.runtime.availableProcessors() - 1 | _
        Runtime.runtime.availableProcessors() + 1 | _
    }

    @Requires(TestPrecondition.ONLINE)
    def "re-runs tests when resources are renamed in a jar"() {
        buildFile << """
            allprojects {
                apply plugin: 'java'
                ${jcenterRepository()}
            }
            dependencies { 
                testImplementation 'junit:junit:4.12'
                testImplementation project(":dependency") 
            }
        """
        settingsFile << """
            include 'dependency'
        """
        file("src/test/java/MyTest.java") << """
            import org.junit.*;

            public class MyTest {
               @Test
               public void test() {
                  Assert.assertNotNull(getClass().getResource("dependency/foo.properties"));
               }
            }
        """.stripIndent()

        def resourceFile = file("dependency/src/main/resources/dependency/foo.properties")
        resourceFile << """
            someProperty = true
        """

        when:
        succeeds 'test'
        then:
        noExceptionThrown()

        when:
        resourceFile.renameTo(file("dependency/src/main/resources/dependency/bar.properties"))
        then:
        fails 'test'
    }

    @Requires(TestPrecondition.ONLINE)
    def "re-runs tests when resources are renamed"() {
        buildFile << """
            apply plugin: 'java'
            ${jcenterRepository()}

            dependencies { 
                testImplementation 'junit:junit:4.12' 
            }
        """
        file("src/test/java/MyTest.java") << """
            import org.junit.*;

            public class MyTest {
               @Test
               public void test() {
                  Assert.assertNotNull(getClass().getResource("dependency/foo.properties"));
               }
            }
        """.stripIndent()

        def resourceFile = file("src/main/resources/dependency/foo.properties")
        resourceFile << """
            someProperty = true
        """

        when:
        succeeds 'test'
        then:
        noExceptionThrown()

        when:
        resourceFile.renameTo(file("src/main/resources/dependency/bar.properties"))
        then:
        fails 'test'
    }

    @Issue("https://github.com/gradle/gradle/issues/3627")
    @ToBeFixedForInstantExecution
    def "can reference properties from TestTaskReports when using @CompileStatic"() {
        buildFile << """
            import groovy.transform.CompileStatic

            @CompileStatic
            class StaticallyCompiledPlugin implements Plugin<Project> {
                @Override
                void apply(Project project) {
                    project.apply plugin: 'java'
                    Test test = (Test) project.tasks.getByName("test")
                    if (test.reports.junitXml.destination.exists()) {
                        println 'JUnit XML report exists!'
                    }
                }
            }

            apply plugin: StaticallyCompiledPlugin
        """

        expect:
        succeeds("tasks")
    }

    private static String standaloneTestClass() {
        return testClass('MyTest')
    }

    private static String testClass(String className) {
        return """
            import org.junit.*;

            public class $className {
               @Test
               public void test() {
                  System.out.println(System.getProperty("java.version"));
                  Assert.assertEquals(1,1);
               }
            }
        """.stripIndent()
    }

    private static String java9Build() {
        """
            apply plugin: 'java'

            ${jcenterRepository()}

            dependencies {
                testImplementation 'junit:junit:4.12'
            }

            sourceCompatibility = 1.9
            targetCompatibility = 1.9
        """
    }

    private static int classFormat(TestFile path) {
        path.bytes[7] & 0xFF
    }
}
