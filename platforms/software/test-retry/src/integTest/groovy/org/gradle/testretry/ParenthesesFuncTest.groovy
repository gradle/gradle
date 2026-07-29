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
package org.gradle.testretry

import static groovy.lang.Tuple2.tuple

class ParenthesesFuncTest extends AbstractPluginFuncTest {

    @Override
    String getLanguagePlugin() {
        "org.jetbrains.kotlin.jvm' version '1.9.23"
    }

    def "should work with parentheses in test name"(Tuple2<Closure<File>, String> scenarios) {
        given:
        def (setupTest, String testSource) = scenarios
        setupTest(buildFile)

        and:
        writeKotlinTestSource testSource

        and:
        // Kotlin Gradle plugin 1.9.23 emits deprecation warnings for APIs used internally
        executer.expectDocumentedDeprecationWarning("The StartParameter.isConfigurationCacheRequested property has been deprecated. This is scheduled to be removed in Gradle 10. Please use 'configurationCache.requested' property on 'BuildFeatures' service instead. Consult the upgrading guide for further information: https://docs.gradle.org/9.8.0-19700101000000+0000/userguide/upgrading_version_8.html#deprecated_startparameter_is_configuration_cache_requested")
        executer.expectDocumentedDeprecationWarning("Declaring a Usage attribute with a legacy value has been deprecated. This will fail with an error in Gradle 10. A Usage attribute was declared with value 'java-api-jars'. Declare a Usage attribute with value 'java-api' and a LibraryElements attribute with value 'jar' instead. Consult the upgrading guide for further information: https://docs.gradle.org/9.8.0-19700101000000+0000/userguide/upgrading_version_9.html#deprecate_legacy_usage_values")
        executer.expectDocumentedDeprecationWarning("Declaring a Usage attribute with a legacy value has been deprecated. This will fail with an error in Gradle 10. A Usage attribute was declared with value 'java-runtime-jars'. Declare a Usage attribute with value 'java-runtime' and a LibraryElements attribute with value 'jar' instead. Consult the upgrading guide for further information: https://docs.gradle.org/9.8.0-19700101000000+0000/userguide/upgrading_version_9.html#deprecate_legacy_usage_values")

        expect:
        succeeds('test')

        where:
        scenarios << [
            tuple({ bf -> setupJunit5Test(bf) }, junit5TestWithParentheses()),
            tuple({ bf -> setupJunit5Test(bf) }, junit5ParameterizedTestWithParentheses()),
            tuple({ bf -> setupJunit4Test(bf) }, junit4TestWithJUnitParams()),
            tuple({ bf -> setupJunit4Test(bf) }, junit4TestWithJUnitParamsWithTestCaseName())
        ]
    }

    private void setupJunit4Test(File buildFile) {
        buildFile << """
            dependencies {
                testImplementation "${junit4Dependency()}"
                testImplementation 'pl.pragmatists:JUnitParams:1.1.1'
                testRuntimeOnly "${junitVintageEngineDependency()}"
                // Since Gradle 9, the JUnit platform launcher is no longer provided by Gradle. 
                testRuntimeOnly "${junitPlatformLauncherDependency()}"
            }

            test {
                useJUnitPlatform()
                retry {
                    maxRetries = 2
                    failOnPassedAfterRetry = false
                }
            }
        """
    }

    private void setupJunit5Test(File buildFile) {
        buildFile << """
            dependencies {
                testImplementation "${jupiterDependency()}"
                testImplementation "${jupiterParamsDependency()}"
                // Since Gradle 9, the JUnit platform launcher is no longer provided by Gradle. 
                testRuntimeOnly "${junitPlatformLauncherDependency()}"
            }

            test {
                useJUnitPlatform()
                retry {
                    maxRetries = 2
                }
            }
        """
    }

    private static String junit4TestWithJUnitParams() {
        """
            package acme

            import junitparams.*
            import org.junit.Test
            import org.junit.runner.RunWith

            @RunWith(JUnitParamsRunner::class)
            class Test1 {

                @Test
                @Parameters("1, true")
                fun test(foo: Int, bar: Boolean) {
                    assert(foo != 0)
                    assert(bar)
                    ${flakyAssert()}
                }
            }
        """
    }

    private static String junit4TestWithJUnitParamsWithTestCaseName() {
        """
            package acme

            import junitparams.*
            import junitparams.naming.*
            import org.junit.Test
            import org.junit.runner.RunWith

            @RunWith(JUnitParamsRunner::class)
            class Test1 {

                @Test
                @Parameters("1, true")
                @TestCaseName("{method}[{index}: {method}({0})={1}]")
                fun test(foo: Int, bar: Boolean) {
                    assert(foo != 0)
                    assert(bar)
                    ${flakyAssert()}
                }
            }
        """
    }

    private static String junit5TestWithParentheses() {
        """
            package acme

            import org.junit.jupiter.api.Test

            class Test1 {

                @Test
                fun `test that contains (parentheses)`() {
                    ${flakyAssert()}
                }
            }
        """
    }

    private static String junit5ParameterizedTestWithParentheses() {
        """
            package acme

            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.Arguments;
            import org.junit.jupiter.params.provider.MethodSource;

            class Test2 {

                @ParameterizedTest
                @MethodSource("data")
                fun `test that contains (parentheses)`(a: Int, b: Int) {
                    assert(a == b)
                    ${flakyAssert()}
                }

                companion object {
                    @JvmStatic
                    fun data() = listOf(
                        Arguments.of(1, 1)
                    )
                }
            }
        """
    }
}
