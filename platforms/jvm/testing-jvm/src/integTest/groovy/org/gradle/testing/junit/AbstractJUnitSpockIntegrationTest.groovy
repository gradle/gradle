/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import spock.lang.Issue

abstract class AbstractJUnitSpockIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    def "can run spock tests with mock of class using gradleApi"() {
        file("build.gradle") << """
            plugins {
                id("groovy")
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation gradleApi()
                implementation localGroovy()
                ${testFrameworkDependencies}
            }

            testing {
                suites {
                    // Must explicitly use `named` to avoid being rewritten by JUnitPlatformTestRewriter.rewriteBuildFile
                    named('test') {
                        useSpock()
                        dependencies {
                            // Required to use Spock mocking
                            runtimeOnly 'net.bytebuddy:byte-buddy:1.12.17'
                        }
                    }
                }
            }
        """
        file("src/main/groovy/MockIt.groovy") << """
            class MockIt {
                void call() {
                }
            }
        """

        file("src/main/groovy/Caller.groovy") << """
            class Caller {
                private MockIt callable

                Caller(MockIt callable) {
                    this.callable = callable
                }

                void call() {
                   callable.call()
                }
            }
        """
        file("src/test/groovy/TestSpec.groovy") << """
            import spock.lang.Specification

            class TestSpec extends Specification {
                def testMethod() {
                    final callable = Mock(MockIt)
                    def caller = new Caller(callable)
                    when:
                    caller.call()
                    then:
                    1 * callable.call()
                    0 * _
                }
            }
        """
        expect:
        succeeds("test")
    }

    def 'can run spock with @Unroll'() {
        given:
        writeSpockDependencies()
        file('src/test/groovy/UnrollTest.groovy') << '''
            import spock.lang.Specification
            import spock.lang.Unroll

            class UnrollTest extends Specification {
                @Unroll
                def "can test #type"() {
                    expect: type

                    where:
                    type << ['1', '2']
                }
            }
        '''
        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(testDirectory)
            .testClass("UnrollTest").assertTestCount(2, 0, 0)
            .assertTestPassed('can test 1')
            .assertTestPassed('can test 2')
    }

    @Issue('https://github.com/gradle/gradle/issues/4358')
    def 'can run spock test with same method name in super class and base class'() {
        given:
        writeSpockDependencies()
        file('src/test/groovy/Base.groovy') << '''
            import spock.lang.Specification

            abstract class Base extends Specification {
                def ok() {
                    expect: "success"
                }
            }

            class Sub extends Base {
                def ok() {
                    expect: "success"
                }
            }
        '''
        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(testDirectory)
            .testClass("Sub").assertTestCount(2, 0, 0)
    }

    def "spock skipped tests print reason with deprecation when tests are selected"() {
        given:
        writeSpockDependencies()
        file("src/test/groovy/MyTest.groovy") << """
            import spock.lang.Specification
            import spock.lang.Requires
            import spock.lang.Unroll

            @Requires(value = { false }, reason = "my reason")
            class MyTest extends Specification {
                def pass() {
                    expect:
                    true
                }
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("All tests skipped. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Tests were discovered and not filtered, but were skipped after discovery. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#test_task_fail_on_no_test_executed")
        succeeds("test")

        then:
        outputContains("""Tests were skipped for the following reasons:
  - Ignored via @Requires: my reason""")
    }

    def "spock skipped tests print reason upon failure when no tests are selected"() {
        given:
        writeSpockDependencies()
        file("src/test/groovy/MyTest.groovy") << """
            import spock.lang.Specification
            import spock.lang.Requires
            import spock.lang.Unroll

            @Requires(value = { false }, reason = "my reason")
            class MyTest extends Specification {
                // Unroll needed so that this test is composite, and so not leaf tests are actually executed/skipped,
                // and therefore the `No tests found for given includes` message is printed
                @Unroll("test name #value")
                def pass() {
                    expect:
                    true

                    where:
                    value << [1, 2]
                }
            }
        """

        when:
        fails("test", "--tests", "MyTest.pass")

        then:
        failure.assertHasCause("""No tests found for given includes: [MyTest.pass](--tests filter)
Tests were skipped for the following reasons:
  - Ignored via @Requires: my reason""")
    }

    private void writeSpockDependencies() {
        file("build.gradle") << """
            apply plugin: 'groovy'

            ${mavenCentralRepository()}

            dependencies {
                ${testFrameworkDependencies}
            }

            testing {
                suites {
                    test {
                        useSpock()
                        dependencies {
                            implementation localGroovy()
                        }
                    }
                }
            }
        """
    }
}
