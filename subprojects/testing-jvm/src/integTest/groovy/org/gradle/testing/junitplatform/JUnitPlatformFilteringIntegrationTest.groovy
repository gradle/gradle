/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.testing.junitplatform

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.util.VersionNumber
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUNIT5_VERSION

@TargetCoverage({ ["5.6.2", LATEST_JUNIT5_VERSION] })
class JUnitPlatformFilteringIntegrationTest extends MultiVersionIntegrationSpec {

    def setup() {
        buildFile << """
            plugins {
                id('java')
            }
            ${mavenCentralRepository()}
            dependencies {
                testImplementation platform('org.junit:junit-bom:$version')
            }
            test {
                useJUnitPlatform()
            }
        """
    }

    def 'can filter nested tests'() {
        given:
        buildFile << """
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter'
            }
        """
        file('src/test/java/org/gradle/NestedTest.java') << '''
            package org.gradle;
            import static org.junit.jupiter.api.Assertions.*;

            import java.util.EmptyStackException;
            import java.util.Stack;

            import org.junit.jupiter.api.*;

            class NestedTest {
                @Test
                void outerTest() {
                }

                @Nested
                class Inner {
                    @Test
                    void innerTest() {
                    }
                }
            }
        '''
        buildFile << '''
            test {
                filter {
                    includeTestsMatching "*innerTest*"
                }
            }
        '''
        when:
        succeeds('test')

        then:
        testResult()
            .assertTestClassesExecuted('org.gradle.NestedTest$Inner')
            .testClass('org.gradle.NestedTest$Inner')
            .assertTestCount(1, 0, 0)
            .assertTestPassed('innerTest()')
    }

    def 'can use nested class as test pattern'() {
        given:
        buildFile << """
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter'
            }
        """
        file('src/test/java/EnclosingClass.java') << '''
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.Nested;
            import static org.junit.jupiter.api.Assertions.assertEquals;

            class EnclosingClass {
                @Nested
                class NestedClass {
                    @Test
                    void nestedTest() {
                    }
                    @Test
                    void anotherTest() {
                    }
                }
                @Nested
                class AnotherNestedClass {
                    @Test
                    void foo() {
                    }
                }
                @Test
                void foo() {
                }
            }
        '''
        when:
        succeeds('test', '--tests', 'EnclosingClass$NestedClass.nestedTest')

        then:
        testResult()
            .assertTestClassesExecuted('EnclosingClass$NestedClass')
            .testClass('EnclosingClass$NestedClass')
            .assertTestCount(1, 0, 0)
            .assertTestPassed('nestedTest')
    }

    @Issue("https://github.com/gradle/gradle/issues/13303")
    @ToBeFixedForConfigurationCache(because = "gradle/configuration-cache#270")
    def "can filter for individual Spock test methods"() {
        given:
        buildFile << """
            apply plugin: 'groovy'

            dependencies {
                testImplementation localGroovy()
                testImplementation 'org.junit.vintage:junit-vintage-engine'
                testImplementation 'org.spockframework:spock-core:1.3-groovy-2.5'
            }
        """
        file("src/test/groovy/TestSpec.groovy") << """
            import spock.lang.*
            class TestSpec extends Specification {
                def test() {
                    expect:
                    true
                }
                @Unroll
                def "#value"() {
                    expect:
                    value
                    where:
                    value << [1, 2]
                }
            }
        """

        when:
        succeeds('test', '--tests', 'TestSpec.test')

        then:
        testResult()
            .assertTestClassesExecuted('TestSpec')
            .testClass('TestSpec')
            .assertTestCount(vintageEngineSupportsMethodSelectorsForSpockMethods() ? 1 : 3, 0, 0)
            .assertTestPassed('test')

        when:
        succeeds('test', '--tests', 'TestSpec.#value')

        then:
        testResult()
            .assertTestClassesExecuted('TestSpec')
            .testClass('TestSpec')
            .assertTestCount(vintageEngineSupportsMethodSelectorsForSpockMethods() ? 2 : 3, 0, 0)
            .assertTestPassed('1')
            .assertTestPassed('2')
    }

    private static boolean vintageEngineSupportsMethodSelectorsForSpockMethods() {
        versionNumber.baseVersion >= VersionNumber.parse("5.7.0")
    }

    private DefaultTestExecutionResult testResult() {
        new DefaultTestExecutionResult(testDirectory)
    }
}
