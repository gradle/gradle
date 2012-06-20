/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.testing.logging

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import spock.lang.Ignore

@Ignore("TODO")
class TestNGLoggingIntegrationTest extends AbstractIntegrationSpec {
    @Rule TestResources resources

    def setup() {
        executer.withStackTraceChecksDisabled().withTasks("test")
    }

    def "defaultLifecycleLogging"() {
        when:
        def result = executer.runWithFailure()

        then:
        result.output.contains("""
org.gradle.TestNGTest > badTest FAILED
    java.lang.RuntimeException at TestNGTest.groovy:38
        """.trim())
    }

    def "defaultInfoLogging"() {
        when:
        def result = executer.withArguments("-i").runWithFailure()

        then:
        result.output.contains("""
org.gradle.TestNGTest > badTest FAILED
    java.lang.RuntimeException: bad
       """.trim())

        // means full stack trace printed
        result.output.contains("at java.lang.reflect.Constructor.newInstance(")

        result.output.contains("""
        at org.gradle.TestNGTest.beBad(TestNGTest.groovy:38)
        at org.gradle.TestNGTest.badTest(TestNGTest.groovy:28)
        """.trim())

        result.output.contains("org.gradle.TestNGTest > ignoredTest SKIPPED")
    }

    def customQuietLogging() {
        when:
        def result = executer.withArguments("-q").runWithFailure()

        then:
        result.output.contains("""
org.gradle.TestNGTest > badTest FAILED
    java.lang.RuntimeException: bad
        at org.gradle.JUnit4Test.beBad(TestNGTest.groovy:38)
        at org.gradle.JUnit4Test.badTest(TestNGTest.groovy:28)

org.gradle.TestNGTest > ignoredTest SKIPPED
org.gradle.TestNGTest FAILED
        """.trim())
    }
}
