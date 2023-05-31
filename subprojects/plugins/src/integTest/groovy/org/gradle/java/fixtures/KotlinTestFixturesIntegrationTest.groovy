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

package org.gradle.java.fixtures

import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import spock.lang.Issue

/**
 * Integration tests for the `java-test-fixtures` plugin that involve `kotlin` projects.
 */
class KotlinTestFixturesIntegrationTest extends AbstractTestFixturesIntegrationTest {
    /**
     * Ensure we make an exception for test fixtures when checking for redundant
     * configuration usage activation - this combination of plugins should not warn.
     */
    @Issue("https://github.com/gradle/gradle/pull/24271")
    def "test kotlin + java-test-fixtures"() {
        given:
        settingsFile << """
            include 'sub'
        """

        file("sub/build.gradle") << """
            plugins {
                id 'java-library'
                id 'java-test-fixtures'
            }
        """

        addPersonDomainClass("sub", "java")
        addPersonTestFixture("sub", "java")

        buildFile.text = """
            plugins {
                id("org.gradle.java-test-fixtures")
                id("org.jetbrains.kotlin.jvm").version("${new KotlinGradlePluginVersions().latest}")
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation('junit:junit:4.13')
                testImplementation(testFixtures(project(":sub")))
            }
        """

        // the test will live in the current project, instead of "sub"
        // which demonstrates that the test fixtures are exposed
        addPersonTestUsingTestFixtures()

        when:
        succeeds ':build'

        then:
        executedAndNotSkipped ':test', ':sub:compileTestFixturesJava'
    }
}
