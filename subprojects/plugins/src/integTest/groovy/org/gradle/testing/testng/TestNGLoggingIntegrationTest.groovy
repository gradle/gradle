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

package org.gradle.testing.testng

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.util.TextUtil
import org.junit.Rule

// can make assumptions about order in which test methods of TestNGTest get executed
// because the methods are chained with 'methodDependsOn'
class TestNGLoggingIntegrationTest extends AbstractIntegrationSpec {
    @Rule TestResources resources = new TestResources(temporaryFolder)
    ExecutionResult result

    def setup() {
        executer.noExtraLogging().withStackTraceChecksDisabled().withTasks("test")
    }

    def "defaultLifecycleLogging"() {
        when:
        result = executer.runWithFailure()

        then:
        outputContains("""
Gradle test > org.gradle.TestNGTest.badTest FAILED
    java.lang.RuntimeException at TestNGTest.groovy:40
        """)
    }

    def customQuietLogging() {
        when:
        result = executer.withArguments("-q").runWithFailure()

        then:
        outputContains("""
org.gradle.TestNGTest.badTest FAILED
    java.lang.RuntimeException: bad
        at org.gradle.TestNGTest.beBad(TestNGTest.groovy:40)
        at org.gradle.TestNGTest.badTest(TestNGTest.groovy:27)

org.gradle.TestNGTest.ignoredTest SKIPPED

Gradle test FAILED
        """)
    }

    def "standardOutputLogging"() {
        when:
        result = executer.withArguments("-q").runWithFailure()

        then:
        outputContains("""
Gradle test > org.gradle.TestNGStandardOutputTest.printTest STANDARD_OUT
    line 1
    line 2
    line 3
        """)
    }

    private void outputContains(String text) {
        assert result.output.contains(TextUtil.toPlatformLineSeparators(text.trim()))
    }
}
