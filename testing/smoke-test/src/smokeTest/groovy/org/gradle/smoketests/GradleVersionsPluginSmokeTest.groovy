/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.integtests.fixtures.modes.ToBeFixedForIsolatedProjects

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class GradleVersionsPluginSmokeTest extends AbstractPluginValidatingSmokeTest {

    @ToBeFixedForIsolatedProjects(because = "Plugin has IP incompatible logic")
    def 'can check for updated versions'() {
        given:
        buildFile << """
            plugins {
                id "io.github.ben-manes.versions" version "$TestedVersions.gradleVersions"
            }
        """
        file("sub1/build.gradle") << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation("log4j:log4j:1.2.14")
            }
        """
        file("sub2/build.gradle") << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation("junit:junit:4.10")
            }
        """
        settingsFile << """
            include "sub1", "sub2"
        """

        when:
        def result = runner('dependencyUpdates', '-DoutputFormatter=txt').build()

        then:
        result.task(':dependencyUpdates').outcome == SUCCESS
        result.output.contains("- junit:junit [4.10 -> 4.13")
        result.output.contains("- log4j:log4j [1.2.14 -> 1.2.17]")

        file("build/dependencyUpdates/report.txt").exists()
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'io.github.ben-manes.versions': Versions.of(TestedVersions.gradleVersions)
        ]
    }
}
