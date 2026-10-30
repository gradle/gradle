/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.GradleVersion

class JavaLibraryMissingDefaultTestSuiteIntegrationTest extends AbstractIntegrationSpec {

    def "suggests Groovy DSL syntax when the default test suite was removed"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            plugins {
                id 'java'
            }

            testing.suites.remove(testing.suites.getByName('test'))

            apply plugin: 'java-library'
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("The default test suite 'test' is required. Root project 'root' has no test suite named 'test' in 'testing.suites'.")
        failure.assertHasResolution("To change the default suite, configure it with testing.suites.named('test', JvmTestSuite) instead of replacing it.")
        failure.assertHasResolution("For more information on configuring the default test suite, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/jvm_test_suite_plugin.html#sec:configuring_the_built_in_test_suite in the Gradle documentation.")
    }

    def "suggests Kotlin DSL syntax when the default test suite was removed"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildKotlinFile """
            plugins {
                java
            }

            testing.suites.remove(testing.suites.getByName("test"))

            apply(plugin = "java-library")
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("The default test suite 'test' is required. Root project 'root' has no test suite named 'test' in 'testing.suites'.")
        failure.assertHasResolution("To change the default suite, configure it with testing.suites.named<JvmTestSuite>(\"test\") instead of replacing it.")
        failure.assertHasResolution("For more information on configuring the default test suite, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/jvm_test_suite_plugin.html#sec:configuring_the_built_in_test_suite in the Gradle documentation.")
    }
}
