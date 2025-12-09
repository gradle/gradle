/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.options

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

class InternalOptionsResolutionIntegrationTest extends AbstractIntegrationSpec {

    def "internal option can be provided as a system property"() {
        settingsFile << printInternalOption()

        when:
        run "help", "--dry-run", "-Dorg.gradle.internal.foo=bar"
        then:
        outputContains("foo='bar'")
    }

    def "internal option can be defined in build root gradle.properties"() {
        settingsFile << printInternalOption()

        propertiesFile """
            org.gradle.internal.foo=bar
        """

        when:
        run "help", "--dry-run"
        then:
        outputContains("foo='bar'")
    }

    def "internal option can be defined in Gradle User Home gradle.properties"() {
        requireOwnGradleUserHomeDir("test modifies gradle.properties within Gradle User Home")

        settingsFile << printInternalOption()

        executer.gradleUserHomeDir.file("gradle.properties") << """
            org.gradle.internal.foo=bar
        """

        when:
        run "help", "--dry-run"
        then:
        outputContains("foo='bar'")
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "internal option can be defined in Gradle Home gradle.properties"() {
        requireIsolatedGradleDistribution()

        settingsFile << printInternalOption()

        distribution.gradleHomeDir.file("gradle.properties") << """
            org.gradle.internal.foo=bar
        """

        when:
        run "help", "--dry-run"
        then:
        outputContains("foo='bar'")
    }

    def "internal option from system property takes precedence over Gradle User Home gradle.properties"() {
        requireOwnGradleUserHomeDir("test modifies gradle.properties within Gradle User Home")

        settingsFile << printInternalOption()

        executer.gradleUserHomeDir.file("gradle.properties") << """
            org.gradle.internal.foo=sport
        """

        when:
        run "help", "--dry-run", "-Dorg.gradle.internal.foo=bar"
        then:
        outputContains("foo='bar'")
    }

    def "internal option from Gradle User Home gradle.properties takes precedence over build root gradle.properties"() {
        requireOwnGradleUserHomeDir("test modifies gradle.properties within Gradle User Home")

        settingsFile << printInternalOption()

        propertiesFile """
            org.gradle.internal.foo=sport
        """

        executer.gradleUserHomeDir.file("gradle.properties") << """
            org.gradle.internal.foo=bar
        """

        when:
        run "help", "--dry-run"
        then:
        outputContains("foo='bar'")
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "internal option from build root gradle.properties takes precedence over Gradle Home gradle.properties"() {
        requireIsolatedGradleDistribution()

        settingsFile << printInternalOption()

        distribution.gradleHomeDir.file("gradle.properties") << """
            org.gradle.internal.foo=sport
        """

        propertiesFile """
            org.gradle.internal.foo=bar
        """

        when:
        run "help", "--dry-run"
        then:
        outputContains("foo='bar'")
    }

    def "internal options are build-tree-scoped and not overridden in included builds"() {
        settingsFile """
            includeBuild("included")
        """

        propertiesFile """
            org.gradle.internal.foo=bar
        """

        file("included/gradle.properties") << """
            org.gradle.internal.foo=sport
        """

        file("included/settings.gradle") << printInternalOption()

        when:
        run "help", "--dry-run"
        then:
        outputContains("foo='bar'")
    }

    private def printInternalOption() {
        //noinspection UnnecessaryQualifiedReference
        buildScriptSnippet """
            def foo = gradle.services.get(org.gradle.internal.buildoption.InternalOptions.class)
                .getOption(org.gradle.internal.buildoption.StringInternalOption.of("org.gradle.internal.foo"))

            println("foo='\${foo.get()}'")
        """
    }
}
