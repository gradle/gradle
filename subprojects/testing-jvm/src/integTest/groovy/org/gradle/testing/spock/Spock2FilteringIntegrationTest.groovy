/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.spock

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class Spock2FilteringIntegrationTest extends Spock2IntegrationSpec {

    def setup() {
        file("src/test/groovy/SuperSuperClass.groovy") << """
            abstract class SuperSuperClass extends spock.lang.Specification {
                def superSuperTest() {
                    expect:
                    true
                }
            }
        """
        file("src/test/groovy/SuperClass.groovy") << """
            abstract class SuperClass extends SuperSuperClass {
                def superTest() {
                    expect:
                    true
                }
            }
        """
        file("src/test/groovy/SubClass.groovy") << """
            class SubClass extends SuperClass {
                def subTest() {
                    expect:
                    true
                }
            }
        """
    }

    @ToBeFixedForConfigurationCache(because = "gradle/configuration-cache#270")
    def "can filter tests from a superclass via a subclass"() {
        when:
        succeeds("test", "--tests", "SubClass.superTest")

        then:
        new DefaultTestExecutionResult(testDirectory)
            .assertTestClassesExecuted("SubClass")
            .testClass("SubClass")
            .assertTestCount(1, 0, 0)
            .assertTestPassed("superTest")
    }

    @ToBeFixedForConfigurationCache(because = "gradle/configuration-cache#270")
    def "can filter tests from a super superclass via a subclass"() {
        when:
        succeeds("test", "--tests", "SubClass.superSuperTest")

        then:
        new DefaultTestExecutionResult(testDirectory)
            .assertTestClassesExecuted("SubClass")
            .testClass("SubClass")
            .assertTestCount(1, 0, 0)
            .assertTestPassed("superSuperTest")
    }

}
