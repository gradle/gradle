/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.testretry.testframework

import org.gradle.testretry.AbstractFrameworkFuncTest
import spock.lang.Issue

import static org.junit.Assume.assumeTrue

abstract class SpockBaseFuncTest extends AbstractFrameworkFuncTest {

    @Override
    String getLanguagePlugin() {
        return 'groovy'
    }

    abstract boolean isRerunsParameterizedMethods()

    abstract boolean canTargetInheritedMethods()

    abstract protected String staticInitErrorTestMethodName()

    abstract protected String beforeClassErrorTestMethodName()

    abstract protected String afterClassErrorTestMethodName()

    def "handles failure in #lifecycle"(String lifecycle) {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeGroovyTestSource """
            package acme

            class FlakySetupSpecTests extends spock.lang.Specification {
                def ${lifecycle}() {
                    ${flakyAssert()}
                }

                def successTest() {
                    expect:
                    true
                }
            }
        """

        when:
        succeeds('test')

        then:
        // will be >1 in the cleanupSpec case, because the test has already reported success
        // before cleanup happens
        if (lifecycle == "setupSpec") {
            with(output) {
                assert it.count("${beforeClassErrorTestMethodName()} FAILED") == 1
                assert it.count("${beforeClassErrorTestMethodName()} PASSED") == 1
                assert it.count('successTest PASSED') == 1
            }
        } else if (lifecycle == "cleanupSpec") {
            with(output) {
                assert it.count("${afterClassErrorTestMethodName()} FAILED") == 1
                assert it.count("${afterClassErrorTestMethodName()} PASSED") == 1
                assert it.count('successTest PASSED') == 2
            }
        } else {
            with(output) {
                assert it.count('successTest FAILED') == 1
                assert it.count('successTest PASSED') == 1
            }
        }

        where:
        lifecycle << ['setup', 'setupSpec', 'cleanup', 'cleanupSpec']
    }

    @spock.lang.PendingFeature(reason = "Test task summary line ('N tests completed') is not captured in output by AbstractIntegrationSpec")
    def "handles flaky static initializers exhaustive = #exhaust"(boolean exhaust) {
        given:
        buildFile << """
            test.retry.maxRetries = 2
        """

        writeGroovyTestSource """
            package acme

            class SomeSpec extends spock.lang.Specification {
                static {
                    ${flakyAssert("id", exhaust ? 3 : 2)}
                }

                def someTest() {
                    expect:
                    true
                }
            }
        """

        when:
        exhaust ? fails('test') : succeeds('test')

        then:
        with(output) {
            assert it.count("SomeSpec > ${staticInitErrorTestMethodName()} FAILED") == (exhaust ? 3 : 2)
            assert it.count("SomeSpec > ${staticInitErrorTestMethodName()} PASSED") == (exhaust ? 0 : 1)
            assert it.count('SomeSpec > someTest PASSED') == (exhaust ? 0 : 1)
        }
        if (exhaust) {
            with(output) {
                assert it.count('3 tests completed, 3 failed') == 1
            }
        } else {
            with(output) {
                assert it.count('4 tests completed, 2 failed') == 1
            }
        }

        where:
        exhaust << [true, false]
    }

    def "handles @Stepwise tests"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeGroovyTestSource """
            package acme

            @spock.lang.Stepwise
            class StepwiseTests extends spock.lang.Specification {
                def "parentTest"() {
                    expect:
                    true
                }

                def "childTest"() {
                    expect:
                    ${flakyAssert()}
                }

                def "grandChildTest"() {
                    expect:
                    true
                }
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('childTest FAILED') == 1
            assert it.count('childTest PASSED') == 1
            assert it.count('parentTest PASSED') == 2

            // grandChildTest gets skipped initially because flaky childTest failed, but is ran as part of the retry
            assert it.count('grandChildTest SKIPPED') == 1
            assert it.count('grandChildTest PASSED') == 1
        }
    }

    @Issue("https://github.com/gradle/test-retry-gradle-plugin/issues/234")
    def "handles @Stepwise tests with maxFailures limit"() {
        given:
        buildFile << """
            test.retry {
                maxRetries = 1
                maxFailures = 1
            }
        """

        writeGroovyTestSource """
            package acme

            @spock.lang.Stepwise
            class StepwiseTests1 extends spock.lang.Specification {
                def "parentTest"() {
                    expect:
                    true
                }

                def "childTest"() {
                    expect:
                    ${flakyAssert('first')}
                }

                def "grandChildTest"() {
                    expect:
                    true
                }
            }

            @spock.lang.Stepwise
            class StepwiseTests2 extends spock.lang.Specification {
                def "parentTest"() {
                    expect:
                    true
                }

                def "childTest"() {
                    expect:
                    ${flakyAssert('second')}
                }

                def "grandChildTest"() {
                    expect:
                    true
                }
            }
        """

        when:
        fails('test')

        then:
        with(output) {
            assert it.count('childTest FAILED') == 2
            assert it.count('childTest PASSED') == 0
            assert it.count('parentTest PASSED') == 2

            assert it.count('grandChildTest SKIPPED') == 2
            assert it.count('grandChildTest PASSED') == 0
        }
    }

    def "can rerun on whole class via className"() {
        given:
        buildFile << """
            test.retry {
                maxRetries = 1
                classRetry {
                    includeClasses.add('*FlakyTests')
                }
            }
        """


        writeGroovyTestSource """
            package acme
            import java.lang.annotation.ElementType
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            import java.lang.annotation.Target

            @Target(ElementType.TYPE)
            @Retention(RetentionPolicy.RUNTIME)
            @interface ClassRetry {

            }
        """

        writeGroovyTestSource """
            package acme

            class FlakyTests extends spock.lang.Specification {
                def "parentTest"() {
                    expect:
                    true
                }

                def "childTest"() {
                    expect:
                    ${flakyAssert()}
                }

                def "grandChildTest"() {
                    expect:
                    true
                }
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('childTest FAILED') == 1
            assert it.count('childTest PASSED') == 1
            assert it.count('parentTest PASSED') == 2
            assert it.count('grandChildTest PASSED') == 2
        }
    }

    def "can rerun on whole class via annotation (retry annotation #retryAnnotation)"(String retryAnnotation) {
        given:
        buildFile << """
            dependencies {
                testImplementation 'com.gradle:develocity-testing-annotations:2.0'
                testImplementation 'com.gradle:gradle-enterprise-testing-annotations:1.1.2'
            }
            test.retry {
                maxRetries = 1
                classRetry {
                    includeAnnotationClasses.add('*CustomClassRetry')
                }
            }
        """


        writeGroovyTestSource """
            package acme
            import java.lang.annotation.ElementType
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            import java.lang.annotation.Target

            @Target(ElementType.TYPE)
            @Retention(RetentionPolicy.RUNTIME)
            @interface CustomClassRetry {

            }
        """

        writeGroovyTestSource """
            package acme

            @$retryAnnotation
            class FlakyTests extends spock.lang.Specification {
                def "parentTest"() {
                    expect:
                    true
                }

                def "childTest"() {
                    expect:
                    ${flakyAssert()}
                }

                def "grandChildTest"() {
                    expect:
                    true
                }
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('childTest FAILED') == 1
            assert it.count('childTest PASSED') == 1
            assert it.count('parentTest PASSED') == 2
            assert it.count('grandChildTest PASSED') == 2
        }

        where:
        retryAnnotation << [
            "acme.CustomClassRetry", "com.gradle.enterprise.testing.annotations.ClassRetry", "com.gradle.develocity.testing.annotations.ClassRetry"
        ]
    }

    def "only track a @Retry test method once to ensure it was re-ran successfully"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeGroovyTestSource """
            package acme

            class RetryTests extends spock.lang.Specification {
                @spock.lang.Retry
                def "retried"() {
                    expect:
                    false
                }
            }
        """

        when:
        fails('test')

        then:
        with(output) {
            assert it.count('retried FAILED') == 2
            assert !it.contains('unable to retry')
        }
    }

    def "handles non-parameterized test names matching a parameterized name"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeGroovyTestSource """
            package acme

            class UnrollTests extends spock.lang.Specification {
                @spock.lang.Unroll
                def "test with #param"() {
                    expect:
                    true

                    where:
                    param << ['foo', 'bar']
                }

                def "test with c"() {
                    expect:
                    ${flakyAssert()}
                }
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('test with c FAILED') == 1
            assert it.count('test with c PASSED') == 1
        }
    }

    def "handles unrolled tests"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeGroovyTestSource """
            package acme

            class UnrollTests extends spock.lang.Specification {

                def passingTest() {
                    expect:
                    true
                }

                @spock.lang.Unroll
                def "unrolled"() {
                    expect:
                    result

                    where:
                    param << ['foo', 'bar', 'baz']
                    result << [true, false, true]
                }

                @spock.lang.Unroll
                def "unrolled with param #p"() {
                    expect:
                    result

                    where:
                    p << ['foo', 'bar', 'baz']
                    result << [true, false, true]
                }

                @spock.lang.Unroll
                def "unrolled with param [#param]"() {
                    expect:
                    result

                    where:
                    param << ['foo', 'bar', 'baz']
                    result << [true, false, true]
                }
            }
        """

        when:
        fails('test')

        then:
        with(output) {
            assert count('passingTest PASSED') == (isRerunsParameterizedMethods() ? 1 : 2)

            with(readLines()) {
                assert count { it =~ /unrolled ?\[.*?0] PASSED/ } == 2
                assert count { it =~ /unrolled ?\[.*?1] FAILED/ } == 2
                assert count { it =~ /unrolled ?\[.*?2] PASSED/ } == 2
            }

            assert count('unrolled with param foo PASSED') == 2
            assert count('unrolled with param bar FAILED') == 2
            assert count('unrolled with param baz PASSED') == 2

            assert count('unrolled with param [foo] PASSED') == 2
            assert count('unrolled with param [bar] FAILED') == 2
            assert count('unrolled with param [baz] PASSED') == 2
        }
    }

    def "handles unrolled tests with method call on param"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeGroovyTestSource """
            package acme

            class UnrollTests extends spock.lang.Specification {

                def passingTest() {
                    expect:
                    true
                }

                @spock.lang.Unroll
                def "unrolled with param [#param.toString().toUpperCase()]"() {
                    expect:
                    result

                    where:
                    param << ['foo', 'bar', 'baz']
                    result << [true, false, true]
                }
            }
        """

        when:
        fails('test')

        then:
        with(output) {
            assert it.count('passingTest PASSED') == (isRerunsParameterizedMethods() ? 1 : 2)

            assert it.count('unrolled with param [FOO] PASSED') == 2
            assert it.count('unrolled with param [BAR] FAILED') == 2
            assert it.count('unrolled with param [BAZ] PASSED') == 2
        }
    }

    def "handles unrolled tests with reserved regex chars"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeGroovyTestSource """
            package acme

            class UnrollTests extends spock.lang.Specification {
                def passingTest() {
                    expect:
                    true
                }

                @spock.lang.Unroll
                def "unrolled with param \\\$.*=.?<>(){}[][^\\\\w]!+- {([#param1])} {([#param2])}"() {
                    expect:
                    result

                    where:
                    param1 << ['foo', 'param_1', 'param1\$1']
                    param2 << ['foo', 'param_2', 'param2']
                    result << [false, false, false]
                }
            }
        """

        when:
        fails('test')

        then:
        with(output) {
            assert it.count('passingTest PASSED') == (isRerunsParameterizedMethods() ? 1 : 2)

            assert it.count('unrolled with param $.*=.?<>(){}[][^\\w]!+- {([foo])} {([foo])} FAILED') == 2
            assert it.count('unrolled with param $.*=.?<>(){}[][^\\w]!+- {([param_1])} {([param_2])} FAILED') == 2
            assert it.count('unrolled with param $.*=.?<>(){}[][^\\w]!+- {([param1\$1])} {([param2])} FAILED') == 2
        }
    }

    def "handles unrolled tests with additional test context method suffix"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeGroovyTestSource contextualTestAnnotation()

        writeGroovyTestSource contextualTestExtension()

        writeGroovyTestSource """
            package acme

            @spock.lang.Unroll
            @ContextualTest
            class UnrollTests extends spock.lang.Specification {

                @spock.lang.Unroll
                def passingTest() {
                    expect:
                    true
                }

                def "unrolled [#param] with additional test context"() {
                    expect:
                    result

                    where:
                    param << ['foo', 'bar', 'baz']
                    result << [false, true, false]
                }
            }
        """

        when:
        fails('test')

        then:
        with(output) {
            assert it.count('passingTest [suffix] PASSED') == (isRerunsParameterizedMethods() ? 1 : 2)

            assert it.count('unrolled [foo] with additional test context [suffix] FAILED') == 2
            assert it.count('unrolled [bar] with additional test context [suffix] PASSED') == 2
            assert it.count('unrolled [baz] with additional test context [suffix] FAILED') == 2
        }
    }

    def "handles unrolled tests with additional test context method suffix in super class"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeGroovyTestSource contextualTestAnnotation()

        writeGroovyTestSource contextualTestExtension()

        writeGroovyTestSource """
            package acme

            @spock.lang.Unroll
            @ContextualTest
            abstract class UnrolledAbstractTests extends spock.lang.Specification {

                def "unrolled parent [#param] with additional test context"() {
                    expect:
                    result

                    where:
                    param << ['foo', 'bar', 'baz']
                    result << [false, true, false]
                }
            }
        """

        writeGroovyTestSource """
            package acme

            class InheritedTests extends UnrolledAbstractTests {

                def "inherited"() {
                    expect:
                    true
                }
            }
        """

        when:
        fails('test')

        then:
        with(output) {
            assert it.count('inherited [suffix] PASSED') == (isRerunsParameterizedMethods() ? 1 : 2)

            assert it.count('unrolled parent [foo] with additional test context [suffix] FAILED') == 2
            assert it.count('unrolled parent [bar] with additional test context [suffix] PASSED') == 2
            assert it.count('unrolled parent [baz] with additional test context [suffix] FAILED') == 2
        }
    }

    def "can rerun on failure in super class with extension added suffix"() {
        given:
        assumeTrue(canTargetInheritedMethods())
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeGroovyTestSource contextualTestAnnotation()

        writeGroovyTestSource contextualTestExtension()

        writeGroovyTestSource """
            package acme

            @ContextualTest
            abstract class AbstractTest extends spock.lang.Specification {
                def "parent"() {
                    expect:
                    ${flakyAssert()}
                }
            }
        """

        writeGroovyTestSource """
            package acme.sub
            import acme.AbstractTest
            class InheritedTest extends AbstractTest {
                def "inherited"() {
                    expect:
                    true
                }
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('parent [suffix] FAILED') == 1
            assert it.count('parent [suffix] PASSED') == 1
            assert it.count('inherited [suffix] PASSED') == (isRerunsParameterizedMethods() ? 1 : 2)
        }
    }

    def "can rerun on failure in super class"() {
        given:
        assumeTrue(canTargetInheritedMethods())
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeGroovyTestSource """
            package acme

            abstract class AbstractTest extends spock.lang.Specification {
                def "parent"() {
                    expect:
                    ${flakyAssert()}
                }
            }
        """

        writeGroovyTestSource """
            package acme.sub
            import acme.AbstractTest
            class InheritedTest extends AbstractTest {
                def "inherited"() {
                    expect:
                    true
                }
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('parent FAILED') == 1
            assert it.count('parent PASSED') == 1
            assert it.count('inherited PASSED') == 1
        }
    }

    def 'can rerun parameterized test method in super class'() {
        given:
        assumeTrue(canTargetInheritedMethods())
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeGroovyTestSource """
            package acme

            @spock.lang.Unroll
            abstract class AbstractTest extends spock.lang.Specification {

                def "unrolled [#param] parent"() {
                    expect:
                    ${flakyAssert()}

                    where:
                    param << ['foo']
                }
            }
        """

        writeGroovyTestSource """
            package acme

            class InheritedTest extends AbstractTest {

                def passingTest() {
                    expect:
                    true
                }

                def "inherited"() {
                    expect:
                    true
                }
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('passingTest PASSED') == (isRerunsParameterizedMethods() ? 1 : 2)

            assert it.count('unrolled [foo] parent FAILED') == 1
            assert it.count('unrolled [foo] parent PASSED') == 1
            assert it.count('inherited PASSED') == (isRerunsParameterizedMethods() ? 1 : 2)
            assert it.count('inherited FAILED') == 0
        }
    }

    def "can rerun on failure in super super class"() {
        given:
        assumeTrue(canTargetInheritedMethods())
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeGroovyTestSource """
            package acme

            abstract class A extends spock.lang.Specification {

                def "a"() {
                    expect:
                    ${flakyAssert()}
                }
            }
        """

        writeGroovyTestSource """
            package acme

            abstract class B extends A {

                def "b"() {
                    expect:
                    false
                }
            }
        """

        writeGroovyTestSource """
            package acme

            class C extends B {

                def "c"() {
                    expect:
                    true
                }
            }
        """

        when:
        fails('test')

        then:
        with(output) {
            assert it.count('a FAILED') == 1
            assert it.count('a PASSED') == 1
            assert it.count('b FAILED') == 2
            assert it.count('c PASSED') == 1
        }
    }

    def "can rerun parameterized test in inherited class defined in a binary"() {
        given:
        assumeTrue(canTargetInheritedMethods())
        settingsFile << """
            include 'dep'
        """
        file("dep/build.gradle") << baseBuildScript().replaceAll("testImplementation", "implementation")

        file("dep/src/main/groovy/acme/FlakyAssert.java") << flakyAssertClass()
        file("dep/src/main/groovy/acme/AbstractTest.groovy") << """
            package acme;

            class AbstractTest extends spock.lang.Specification {
                @spock.lang.Unroll
                def "unrolled [#param] parent"() {
                    expect:
                    ${flakyAssert()}

                    where:
                    param << ["foo"]
                }
            }
        """
        buildFile << """
            test.retry.maxRetries = 1

            dependencies {
                testImplementation project(":dep")
            }
        """

        writeGroovyTestSource """
            package acme

            class B extends AbstractTest {
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('unrolled [foo] parent FAILED') == 1
            assert it.count('unrolled [foo] parent PASSED') == 1
        }
    }

    @Issue("https://github.com/gradle/test-retry-gradle-plugin/issues/52")
    @spock.lang.PendingFeature(reason = "Test task summary line ('N tests completed') is not captured in output by AbstractIntegrationSpec")
    def "test that is skipped after failure is considered to be still failing"() {
        given:
        buildFile << """
            test.retry.maxRetries = 3
            test.retry.failOnPassedAfterRetry = false
        """

        writeGroovyTestSource """
            package acme
            import java.nio.file.Paths
            import java.nio.file.Files

            class Tests extends spock.lang.Specification {

                @spock.lang.IgnoreIf({${markerFileExistsCheck()}})
                def "a"() {
                    expect:
                    ${flakyAssert()}
                }
            }
        """

        when:
        fails('test')

        then:
        with(output) {
            assert it.count('a FAILED') == 1
            assert it.count('a SKIPPED') == 3
            assert it.count('a PASSED') == 0
            assert it.contains('4 tests completed, 1 failed, 3 skipped')
            assert !it.contains('Please file a bug report at')
        }
    }

    @Issue("https://github.com/gradle/test-retry-gradle-plugin/issues/52")
    @spock.lang.PendingFeature(reason = "Test task summary line ('N tests completed') is not captured in output by AbstractIntegrationSpec")
    def "test that is ignored after failure is considered to be still failing"() {
        given:
        buildFile << """
            test.retry.maxRetries = 3
            test.retry.failOnPassedAfterRetry = false
        """

        writeGroovyTestSource """
            package acme
            import java.nio.file.Paths
            import java.nio.file.Files

            @spock.lang.Unroll
            class FlakyTest extends spock.lang.Specification {

                @spock.lang.IgnoreIf({${markerFileExistsCheck()}})
                def "a"() {
                    expect:
                    ${flakyAssert()}
                }
            }
        """

        when:
        fails('test')

        then:
        with(output) {
            assert it.count('a FAILED') == 1
            assert it.count('a SKIPPED') == 3
            assert it.count('a PASSED') == 0
            assert it.contains('4 tests completed, 1 failed, 3 skipped')
            assert !it.contains('Please file a bug report at')
        }
    }

    @spock.lang.PendingFeature(reason = "Test task summary line ('N tests completed') is not captured in output by AbstractIntegrationSpec")
    def "build is successful if a test is ignored but never failed"() {
        given:
        buildFile << """
            test.retry.maxRetries = 2
            test.retry.failOnPassedAfterRetry = false
        """

        writeGroovyTestSource """
            package acme
            import java.nio.file.Paths
            import java.nio.file.Files

            @spock.lang.Stepwise
            class StepwiseTests extends spock.lang.Specification {
                def "parentTest"() {
                    expect:
                    ${flakyAssert()}
                }

                @spock.lang.Ignore
                def "childTest"() {
                    expect:
                    true
                }
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('childTest SKIPPED') == 2
            assert it.count('parentTest FAILED') == 1
            assert it.count('parentTest PASSED') == 1
            assert it.contains('4 tests completed, 1 failed, 2 skipped')
            assert !it.contains('Please file a bug report at')
        }
    }

    def "handles flaky setup that prevents the retries of initially failed methods"() {
        given:
        buildFile << """
            test.retry.maxRetries = 2
        """

        and:
        writeGroovyTestSource """
            package acme

            class FlakySetupAndMethodTest extends spock.lang.Specification {

                def setupSpec() {
                    ${flakyAssertPassFailPass("setup")}
                }

                def flakyTest() {
                    expect:
                    ${flakyAssert("method")}
                }

                def successfulTest() {
                    expect:
                    true
                }
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('flakyTest FAILED') == 1
            assert it.count("${beforeClassErrorTestMethodName()} FAILED") == 1
            assert it.count("${beforeClassErrorTestMethodName()} PASSED") == 1
            assert it.count('flakyTest PASSED') == 1
            assert it.count('successfulTest PASSED') == 2
        }
    }

    def "handles setup failure after cleanup failure"() {
        given:
        buildFile << """
            test.retry.maxRetries = 2
        """

        and:
        writeGroovyTestSource """
            package acme

            class FlakySetupAndMethodTest extends spock.lang.Specification {

                def setupSpec() {
                    ${flakyAssertPassFailPass("setup")}
                }

                def cleanupSpec() {
                    ${flakyAssert("cleanup")}
                }

                def flakyTest() {
                    expect:
                    ${flakyAssert("method")}
                }

                def successfulTest() {
                    expect:
                    true
                }
            }
        """

        when:
        succeeds('test')

        then:
        def differentiatesBetweenSetupAndCleanupMethods = beforeClassErrorTestMethodName() != afterClassErrorTestMethodName()
        with(output) {
            assert it.count('flakyTest FAILED') == 1
            assert it.count('flakyTest PASSED') == 1
            assert it.count('successfulTest PASSED') == 2

            if (differentiatesBetweenSetupAndCleanupMethods) {
                assert it.count("${afterClassErrorTestMethodName()} FAILED") == 1
                assert it.count("${afterClassErrorTestMethodName()} PASSED") == 1
                assert it.count("${beforeClassErrorTestMethodName()} FAILED") == 1
                assert it.count("${beforeClassErrorTestMethodName()} PASSED") == 1
            } else {
                assert it.count("${beforeClassErrorTestMethodName()} FAILED") == 2
                assert it.count("${beforeClassErrorTestMethodName()} PASSED") == 1
            }
        }
    }

    def "can retry tests with @Unroll template different from the method name"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        and:
        writeGroovyTestSource """
            package acme

            class UnrollTemplateTest extends spock.lang.Specification {

                @spock.lang.Unroll("test for #a")
                def customUnroll() {
                    expect:
                    ${flakyAssert("customUnroll")}

                    where:
                    a << [1, 2]
                }


                def successfulTest() {
                    expect:
                    true
                }
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('test for 1 FAILED') == 1
            assert it.count('test for 1 PASSED') == 1
            assert it.count('test for 2 PASSED') == 2
            assert it.count('successfulTest PASSED') == (isRerunsParameterizedMethods() ? 1 : 2)
        }
    }

    def "retries only matching unrolled methods if other methods match the template"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        and:
        writeGroovyTestSource """
            package acme

            class UnrollTemplateTest extends spock.lang.Specification {

                @spock.lang.Unroll("test for #a")
                def customUnroll() {
                    expect:
                    ${flakyAssert("customUnroll")}

                    where:
                    a << [1, 2]
                }


                def "test for c"() {
                    expect:
                    true
                }
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('test for 1 FAILED') == 1
            assert it.count('test for 1 PASSED') == 1
            assert it.count('test for 2 PASSED') == 2
            assert it.count('test for c PASSED') == (isRerunsParameterizedMethods() ? 1 : 2)
        }
    }

    def "reruns matching unrolled methods if other methods matching the template failed"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        and:
        writeGroovyTestSource """
            package acme

            class UnrollTemplateTest extends spock.lang.Specification {

                @spock.lang.Unroll("test for #a")
                def customUnroll() {
                    expect:
                    true

                    where:
                    a << [1, 2]
                }


                def "test for c"() {
                    expect:
                    ${flakyAssert("customUnroll")}
                }
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('test for 1 PASSED') == 2
            assert it.count('test for 2 PASSED') == 2
            assert it.count('test for c FAILED') == 1
            assert it.count('test for c PASSED') == 1
        }
    }

    @Override
    protected String buildConfiguration() {
        return """
            dependencies {
                implementation "org.codehaus.groovy:groovy:2.5.8"
                testImplementation "${spock1Dependency()}"
            }
        """
    }

    protected String contextualTestExtension() {
        // language=Groovy
        """
            package acme

            import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension
            import org.spockframework.runtime.model.SpecInfo

            class ContextualTestExtension extends AbstractAnnotationDrivenExtension<ContextualTest> {

                @Override
                void visitSpecAnnotation(ContextualTest annotation, SpecInfo spec) {

                    spec.features.each { feature ->
                        feature.reportIterations = true
                        def currentNameProvider = feature.iterationNameProvider
                        feature.iterationNameProvider = {
                            def defaultName = currentNameProvider != null ? currentNameProvider.getName(it) : feature.name
                            defaultName + " [suffix]"
                        }
                    }
                }
            }
        """
    }

    private static String contextualTestAnnotation() {
        // language=Groovy
        """
            package acme

            import org.spockframework.runtime.extension.ExtensionAnnotation
            import java.lang.annotation.*

            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.TYPE])
            @Inherited
            @ExtensionAnnotation(ContextualTestExtension)
            @interface ContextualTest {
            }
        """
    }

    @Override
    protected void successfulTest() {
        writeGroovyTestSource """
            package acme;

            public class SuccessfulTests extends spock.lang.Specification {
                public void successTest() {
                    expect:
                    true
                }
            }
        """
    }

    @Override
    protected void flakyTest() {
        writeGroovyTestSource """
            package acme;

            public class FlakyTests extends spock.lang.Specification {
                public void flaky() {
                    expect:
                    ${flakyAssert()}
                }
            }
        """
    }
}
