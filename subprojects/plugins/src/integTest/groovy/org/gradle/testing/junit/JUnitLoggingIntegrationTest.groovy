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

package org.gradle.testing.junit

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.ExecutionResult
import org.gradle.util.TextUtil
import org.junit.Rule

// cannot make assumptions about order in which test methods of JUnit4Test get executed
class JUnitLoggingIntegrationTest extends AbstractIntegrationSpec {
    @Rule TestResources resources
    ExecutionResult result

    def setup() {
        executer.setAllowExtraLogging(false).withStackTraceChecksDisabled().withTasks("test")
    }

    def "defaultLifecycleLogging"() {
        when:
        result = executer.runWithFailure()

        then:
        outputContains("""
org.gradle.JUnit4Test > badTest FAILED
    java.lang.RuntimeException at JUnit4Test.groovy:44
        """)
    }

    def "customQuietLogging"() {
        when:
        result = executer.withArguments("-q").runWithFailure()

        then:
        outputContains("""
badTest FAILED
    java.lang.RuntimeException: bad
        at org.gradle.JUnit4Test.beBad(JUnit4Test.groovy:44)
        at org.gradle.JUnit4Test.badTest(JUnit4Test.groovy:28)
        """)

        outputContains("ignoredTest SKIPPED")

        outputContains("org.gradle.JUnit4Test FAILED")
    }

    def "standardOutputLogging"() {
        when:
        result = executer.withArguments("-q").runWithFailure()

        then:
        outputContains("""
org.gradle.JUnit4StandardOutputTest > printTest STANDARD_OUT
    line 1
    line 2
    line 3
        """)
    }

    private void outputContains(String text) {
        assert result.output.contains(TextUtil.toPlatformLineSeparators(text.trim()))
    }
}
