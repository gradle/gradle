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

class DevelocityPluginIntegrationFuncTest extends AbstractGeneralPluginFuncTest {

    def setup() {
        // These scenarios all use failedTest() which throws an AssertionError; the JUnit
        // assertion trace is printed to stdout and gradle/gradle's default executer flags
        // any unexpected stack trace on stdout as a test failure. Turn that check off
        // because the whole point of these tests is to observe how the plugin behaves in
        // the presence of failing tests.
        executer.withStackTraceChecksDisabled()

        buildFile << """
            test.retry.maxRetries = 1
        """
    }

    def "is deactivated when decorated #extensionType extension returns true"(DslExtensionType extensionType) {
        given:
        failedTest()
        buildFile << extensionType.getSnippet('true')

        when:
        fails('test', '--info')

        then:
        assertNotRetried()
        output.contains("handled by the Develocity plugin")

        where:
        extensionType << DslExtensionType.values()
    }

    def "is deactivated when decorated #extensionType extension changes to true"(DslExtensionType extensionType) {
        given:
        successfulTest() // a failing one prohibit task outputs from being cached
        buildFile << extensionType.getSnippet('Boolean.getBoolean("shouldTestRetryPluginBeDeactivated")')

        when:
        succeeds('test', '--info', '-DshouldTestRetryPluginBeDeactivated=true')

        then:
        output.contains("handled by the Develocity plugin")

        when:
        succeeds('test', '--info', '-DshouldTestRetryPluginBeDeactivated=false')

        then:
        with(output) {
            assert !contains("handled by the Develocity plugin")
            assert !contains("> Task :test UP-TO-DATE")
        }

        where:
        extensionType << DslExtensionType.values()
    }

    def "is deactivated when undecorated #extensionType extension returns true"(DslExtensionType extensionType) {
        given:
        failedTest()
        buildFile << extensionType.getSnippet(result: 'true', decorated: false)

        when:
        fails('test')

        then:
        assertNotRetried()

        where:
        extensionType << DslExtensionType.values()
    }

    def "is not deactivated when #extensionType extension returns false"(DslExtensionType extensionType) {
        given:
        failedTest()
        buildFile << extensionType.getSnippet('false')

        when:
        fails('test')

        then:
        assertRetried()

        where:
        extensionType << DslExtensionType.values()
    }

    def "is not deactivated when distribution extension does not declare the expected method"() {
        given:
        failedTest()
        buildFile << DslExtensionType.DISTRIBUTION.getSnippet(decorated: false, addMethod: false)

        when:
        fails('test')

        then:
        assertRetried()
    }

    def "develocity extension takes precedence"() {
        given:
        failedTest()
        buildFile << DslExtensionType.DISTRIBUTION.getSnippet('false') << DslExtensionType.DEVELOCITY.getSnippet('true')

        when:
        fails('test', '--info')

        then:
        assertNotRetried()
        output.contains("handled by the Develocity plugin")
    }

    void assertRetried() {
        assertRetries(1)
    }

    void assertNotRetried() {
        assertRetries(0)
    }

    void assertRetries(int retries) {
        // 1 initial + retries + 1 overall task FAILED
        // AbstractIntegrationSpec's `output` excludes the "BUILD FAILED" summary line, so we
        // don't count that final "1 build FAILED" that the upstream TestKit-based test tallied.
        with(output) {
            assert it.count('FAILED') == 1 + retries + 1
        }
        assertTestReportContains("FailedTests", reportedTestName("failedTest"), 0, 1 + retries)
    }
}
