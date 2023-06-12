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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import spock.lang.Issue

import static org.gradle.api.internal.DocumentationRegistry.BASE_URL
import static org.gradle.api.internal.DocumentationRegistry.RECOMMENDATION

abstract class AbstractTestTaskIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    abstract String getStandaloneTestClass()
    abstract String testClass(String className)

    def setup() {
        buildFile << """
            allprojects {
                apply plugin: 'java'

                repositories {
                    mavenCentral()
                }

                dependencies {
                    ${testFrameworkDependencies}
                }

                test.${configureTestFramework}
            }
        """
    }

    @Issue("GRADLE-2702")
    @ToBeFixedForConfigurationCache(because = "early dependency resolution")
    def "should not resolve configuration results when there are no tests"() {
        given:
        buildFile << """
            configurations.all {
                if (it.canBeResolved) {
                    incoming.beforeResolve { throw new RuntimeException() }
                }
            }
        """

        when:
        run("build")

        then:
        noExceptionThrown()
    }

    def "test task is skipped when there are no tests"() {
        given:
        file("src/test/java/not_a_test.txt")

        when:
        run("build")

        then:
        result.assertTaskSkipped(":test")
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "compiles and executes a Java 9 test suite"() {
        given:
        buildFile << java9Build()

        file('src/test/java/MyTest.java') << standaloneTestClass

        when:
        succeeds 'test'

        then:
        noExceptionThrown()

        and:
        classFormat(classFile('java', 'test', 'MyTest.class')) == 53

    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "compiles and executes a Java 9 test suite even if a module descriptor is on classpath"() {
        given:
        buildFile << java9Build()

        file('src/test/java/MyTest.java') << standaloneTestClass
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

    def "test task does not hang if maxParallelForks is greater than max-workers (#maxWorkers)"() {
        given:
        def maxParallelForks = maxWorkers + 1

        and:
        2000.times { num ->
            file("src/test/java/SomeTest${num}.java") << testClass("SomeTest${num}")
        }

        and:
        buildFile << """
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

    @Requires(UnitTestPreconditions.Online)
    def "re-runs tests when resources are renamed in a jar"() {
        given:
        buildFile << """
            dependencies {
                testImplementation project(":dependency")
            }
        """
        settingsFile << """
            include 'dependency'
        """
        file("src/test/java/MyTest.java") << """
            ${testFrameworkImports}

            public class MyTest {
               @Test
               public void test() {
                  assertNotNull(getClass().getResource("dependency/foo.properties"));
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

    @Requires(UnitTestPreconditions.Online)
    def "re-runs tests when resources are renamed"() {
        given:
        file("src/test/java/MyTest.java") << """
            ${testFrameworkImports}

            public class MyTest {
               @Test
               public void test() {
                  assertNotNull(getClass().getResource("dependency/foo.properties"));
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
    def "can reference properties from TestTaskReports when using @CompileStatic"() {
        given:
        buildFile.text = """
            import groovy.transform.CompileStatic

            @CompileStatic
            class StaticallyCompiledPlugin implements Plugin<Project> {
                @Override
                void apply(Project project) {
                    project.apply plugin: 'java'
                    Test test = (Test) project.tasks.getByName("test")
                    if (test.reports.junitXml.outputLocation.asFile.get().exists()) {
                        println 'JUnit XML report exists!'
                    }
                }
            }

            apply plugin: StaticallyCompiledPlugin
        """

        expect:
        succeeds("help")
    }

    def "reports failure of TestExecuter regardless of filters"() {
        given:
        file('src/test/java/MyTest.java') << standaloneTestClass
        buildFile << """
            import org.gradle.api.internal.tasks.testing.*

            test {
                doFirst {
                    testExecuter = new TestExecuter<JvmTestExecutionSpec>() {
                        @Override
                        void execute(JvmTestExecutionSpec testExecutionSpec, TestResultProcessor resultProcessor) {
                            DefaultTestSuiteDescriptor suite = new DefaultTestSuiteDescriptor(testExecutionSpec.path, testExecutionSpec.path)
                            resultProcessor.started(suite, new TestStartEvent(System.currentTimeMillis()))
                            try {
                                throw new RuntimeException("boom!")
                            } finally {
                                resultProcessor.completed(suite.getId(), new TestCompleteEvent(System.currentTimeMillis()))
                            }
                        }

                        @Override
                        void stopNow() {
                            // do nothing
                        }
                    }
                }
            }
        """

        when:
        fails("test", *extraArgs)

        then:
        result.assertHasCause('boom!')

        where:
        extraArgs << [[], ["--tests", "MyTest"]]
    }

    def "test framework can be set to the same value twice"() {
        given:
        file('src/test/java/MyTest.java') << standaloneTestClass

        settingsFile << "rootProject.name = 'Sample'"
        buildFile << """
            test {
                $configureTestFramework
                $configureTestFramework
            }
        """.stripIndent()

        expect:
        succeeds("test")
    }

    def "options configured after setting test framework"() {
        given:
        file('src/test/java/MyTest.java') << standaloneTestClass

        settingsFile << "rootProject.name = 'Sample'"
        buildFile << """
            test {
                options {
                    ${excludeCategoryOrTag("MyTest\$Slow")}
                }
            }

            tasks.register('verifyTestOptions') {
                def categoryOrTagExcludes = provider {
                    tasks.getByName("test").getOptions().${buildScriptConfiguration.excludeCategoryOrTagConfigurationElement}
                }
                doLast {
                    assert categoryOrTagExcludes.get().contains("MyTest\\\$Slow")
                }
            }
        """.stripIndent()

        expect:
        succeeds("test", "verifyTestOptions", "--warn")
    }

    def "setForkEvery null emits deprecation warning"() {
        given:
        buildFile << """
            tasks.withType(Test).configureEach {
                forkEvery = null
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("Setting Test.forkEvery to null. This behavior has been deprecated. " +
            "This will fail with an error in Gradle 9.0. Set Test.forkEvery to 0 instead. " +
            String.format(RECOMMENDATION, "information", "${BASE_URL}/dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:forkEvery"))

        then:
        succeeds "test", "--dry-run"
    }

    def "setForkEvery Long emits deprecation warning"() {
        given:
        buildFile << """
            tasks.withType(Test).configureEach {
                setForkEvery(Long.valueOf(1))
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("The Test.setForkEvery(Long) method has been deprecated. " +
            "This is scheduled to be removed in Gradle 9.0. Please use the Test.setForkEvery(long) method instead. " +
            String.format(RECOMMENDATION, "information", "${BASE_URL}/dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:forkEvery"))

        then:
        succeeds "test", "--dry-run"
    }

    private String java9Build() {
        """
            java {
                sourceCompatibility = 1.9
                targetCompatibility = 1.9
            }
        """
    }

    private static int classFormat(TestFile path) {
        path.bytes[7] & 0xFF
    }
}
