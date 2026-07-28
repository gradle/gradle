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

import org.gradle.testkit.runner.BuildResult

import static groovy.util.GroovyCollections.combinations

class DevelocityPluginIntegrationFuncTest extends AbstractGeneralPluginFuncTest {

    def setup() {
        buildFile << """
            test.retry.maxRetries = 1
        """
    }

    def "is deactivated when decorated #extensionType extension returns true [gradle version #gradleVersion]"(String gradleVersion, DslExtensionType extensionType) {
        given:
        failedTest()
        buildFile << extensionType.getSnippet('true')

        when:
        def result = gradleRunner(gradleVersion, 'test', '--info').buildAndFail()

        then:
        assertNotRetried(result, gradleVersion)
        result.output.contains("handled by the Develocity plugin")

        where:
        //noinspection GroovyAssignabilityCheck
        [gradleVersion, extensionType] << combinations(GRADLE_VERSIONS_UNDER_TEST, DslExtensionType.values())
    }

    def "is deactivated when decorated #extensionType extension changes to true [gradle version #gradleVersion]"(String gradleVersion, DslExtensionType extensionType) {
        given:
        successfulTest() // a failing one prohibit task outputs from being cached
        buildFile << extensionType.getSnippet('Boolean.getBoolean("shouldTestRetryPluginBeDeactivated")')

        when:
        def result = gradleRunner(gradleVersion, 'test', '--info', '-DshouldTestRetryPluginBeDeactivated=true').build()

        then:
        result.output.contains("handled by the Develocity plugin")

        when:
        result = gradleRunner(gradleVersion, 'test', '--info', '-DshouldTestRetryPluginBeDeactivated=false').build()

        then:
        with(result.output) {
            !contains("handled by the Develocity plugin")
            !contains("> Task :test UP-TO-DATE")
        }

        where:
        //noinspection GroovyAssignabilityCheck
        [gradleVersion, extensionType] << combinations(GRADLE_VERSIONS_UNDER_TEST, DslExtensionType.values())
    }

    def "is deactivated when undecorated #extensionType extension returns true [gradle version #gradleVersion]"(String gradleVersion, DslExtensionType extensionType) {
        given:
        failedTest()
        buildFile << extensionType.getSnippet(result: 'true', decorated: false)

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        assertNotRetried(result, gradleVersion)

        where:
        //noinspection GroovyAssignabilityCheck
        [gradleVersion, extensionType] << combinations(GRADLE_VERSIONS_UNDER_TEST, DslExtensionType.values())
    }

    def "is not deactivated when #extensionType extension returns false [gradle version #gradleVersion]"(String gradleVersion, DslExtensionType extensionType) {
        given:
        failedTest()
        buildFile << extensionType.getSnippet('false')

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        assertRetried(result, gradleVersion)

        where:
        //noinspection GroovyAssignabilityCheck
        [gradleVersion, extensionType] << combinations(GRADLE_VERSIONS_UNDER_TEST, DslExtensionType.values())
    }

    def "is not deactivated when distribution extension does not declare the expected method [gradle version #gradleVersion]"() {
        given:
        failedTest()
        buildFile << DslExtensionType.DISTRIBUTION.getSnippet(decorated: false, addMethod: false)

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        assertRetried(result, gradleVersion)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "develocity extension takes precedence [gradle version #gradleVersion]"() {
        given:
        failedTest()
        buildFile
            << DslExtensionType.DISTRIBUTION.getSnippet('false')
            << DslExtensionType.DEVELOCITY.getSnippet('true')

        when:
        def result = gradleRunner(gradleVersion, 'test', '--info').buildAndFail()

        then:
        assertNotRetried(result, gradleVersion)
        result.output.contains("handled by the Develocity plugin")

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def assertRetried(BuildResult result, String gradleVersion) {
        assertRetries(result, 1)
    }

    def assertNotRetried(BuildResult result, String gradleVersion) {
        assertRetries(result, 0)
    }

    def assertRetries(BuildResult result, int retries) {
        // 1 initial + retries + 1 overall task FAILED + 1 build FAILED
        with(result.output) {
            it.count('FAILED') == 1 + retries + 1 + 1
        }
        assertTestReportContains("FailedTests", reportedTestName("failedTest"), 0, 1 + retries)
    }
}
