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

package org.gradle.launcher

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class StacktraceIntegrationTest extends AbstractIntegrationSpec {

    def setup () {
        buildFile << 'throw new RuntimeException("show stacktrace was " + gradle.startParameter.showStacktrace)'
        settingsFile << 'rootProject.name = "stacktrace-integration-test-sample"'
    }

    def "no stacktrace is present in the output by default"() {
        when:
        fails()

        then:
        assertCauseWithoutStacktrace('show stacktrace was INTERNAL_EXCEPTIONS')
    }

    def "can configure in gradle.properties file"() {
        setup:
        file('gradle.properties') << 'org.gradle.logging.stacktrace=full'

        when:
        fails()

        then:
        assertCauseWithStacktrace('show stacktrace was ALWAYS_FULL')
    }

    def "configuration is case-insensitive"() {
        setup:
        file('gradle.properties') << 'org.gradle.logging.stacktrace=FuLl'

        when:
        fails()

        then:
        assertCauseWithStacktrace('show stacktrace was ALWAYS_FULL')
    }

    def "can enable from the command line even if it's disabled in gradle.properties"() {
        setup:
        file('gradle.properties') << 'org.gradle.logging.stacktrace=internal'
        executer.withArgument('--stacktrace')

        when:
        fails()

        then:
        assertCauseWithStacktrace('show stacktrace was ALWAYS')
    }

    def "emits actionable message when wrong configuration is used"() {
        setup:
        executer.requireDaemon().requireIsolatedDaemons()
        file('gradle.properties') << 'org.gradle.logging.stacktrace=suppress'

        when:
        fails()

        then:
        assertCauseWithStacktrace(null, "Value 'suppress' given for org.gradle.logging.stacktrace Gradle property is invalid (must be one of internal, all, or full)", "java.lang.IllegalArgumentException")
    }

    private void assertCauseWithoutStacktrace(String cause, String description = "A problem occurred evaluating root project 'stacktrace-integration-test-sample'") {
        failure.assertHasDescription(description)
        failure.assertHasCause(cause)
        failure.assertNotOutput('Exception is:')
    }

    private void assertCauseWithStacktrace(String cause, String description = "A problem occurred evaluating root project 'stacktrace-integration-test-sample'", String stackFrame = 'org.gradle.api.GradleScriptException: A problem occurred evaluating root project') {
        if (cause != null) {
            failure.assertHasCause(cause)
        }
        failure.assertHasDescription(description)
        failure.assertHasErrorOutput('Exception is:')
        failure.assertHasErrorOutput(stackFrame)
    }
}
