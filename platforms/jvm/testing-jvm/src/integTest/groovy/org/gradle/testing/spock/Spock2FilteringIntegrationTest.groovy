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

class Spock2FilteringIntegrationTest extends Spock2IntegrationSpec {

    def setup() {
        def testBody = """
            {
                expect:
                true
            }
        """
        def unrolledTestBody = """
            {
                expect:
                true

                where:
                param << ["value1", "value2"]
            }
        """

        file("src/test/groovy/SuperSuperClass.groovy") << """
            abstract class SuperSuperClass extends spock.lang.Specification {
                def "super super test"() $testBody
                def "super super unrolled test"() $unrolledTestBody
                def "super super unrolled test param=#param"() $unrolledTestBody
            }
        """
        file("src/test/groovy/SuperClass.groovy") << """
            abstract class SuperClass extends SuperSuperClass {
                def "super test"() $testBody
                def "super unrolled test"() $unrolledTestBody
                def "super unrolled test param=#param"() $unrolledTestBody
            }
        """
        file("src/test/groovy/SubClass.groovy") << """
            class SubClass extends SuperClass {
                def "sub test"() $testBody
                def "sub unrolled test"() $unrolledTestBody
                def "sub unrolled test param=#param"() $unrolledTestBody
            }
        """
    }

    def "can filter tests"() {
        when:
        succeeds("test", "--tests", "SubClass.$testMethod")

        then:
        new DefaultTestExecutionResult(testDirectory)
            .assertTestClassesExecuted("SubClass")
            .testClass("SubClass")
            .assertTestCount(1, 0, 0)
            .assertTestPassed(testMethod)

        where:
        testMethod << ["sub test", "super test", "super super test"]
    }

    def "can filter unrolled tests"() {
        when:
        succeeds("test", "--tests", "SubClass.$testMethod")

        then:
        new DefaultTestExecutionResult(testDirectory)
            .assertTestClassesExecuted("SubClass")
            .testClass("SubClass")
            .assertTestCount(2, 0, 0)
            .assertTestPassed("$testMethod [param: value1, #0]")
            .assertTestPassed("$testMethod [param: value2, #1]")

        where:
        testMethod << ["sub unrolled test", "super unrolled test", "super super unrolled test"]
    }

    def "can filter unrolled tests with parameter name in test header"() {
        when:
        succeeds("test", "--tests", "SubClass.$testMethod param=#param")

        then:
        new DefaultTestExecutionResult(testDirectory)
            .assertTestClassesExecuted("SubClass")
            .testClass("SubClass")
            .assertTestCount(2, 0, 0)
            .assertTestPassed("$testMethod param=value1")
            .assertTestPassed("$testMethod param=value2")

        where:
        testMethod << ["sub unrolled test", "super unrolled test", "super super unrolled test"]
    }

    def "can not filter specific iterations of unrolled tests"() {
        when:
        fails("test", "--tests", "SubClass.$testMethod")

        then:
        failureCauseContains("No tests found for given includes: [SubClass.$testMethod](--tests filter)")

        where:
        testMethod << ["sub unrolled test param=value1", "super unrolled test param=value1", "super super unrolled test param=value1"]
    }

}
