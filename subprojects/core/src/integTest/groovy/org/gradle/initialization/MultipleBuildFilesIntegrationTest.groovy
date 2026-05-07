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

package org.gradle.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class MultipleBuildFilesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()
    }

    def "warns when multiple build files exist in the same directory"() {
        given:
        // Create multiple build files
        file("build.gradle") << """
            // This is the selected build file
        """
        file("build.gradle.kts") << """
            // This file exists but should be ignored
        """
        file("build.gradle.dcl") << """
            // This file exists but should be ignored
        """

        when:
        succeeds('help')

        then:
        // Verify that a problem was reported
        verifyAll(receivedProblem) {
            definition.id.fqid == 'scripts:multiple-scripts'
            definition.id.displayName == 'Multiple scripts'
            contextualLabel == "Multiple build script files were found in directory '${testDirectory}'"
            details.contains("Selected 'build.gradle'")
            details.contains("ignoring 'build.gradle.kts'")
            solutions.size() == 1
            def solution = solutions[0]
            solution.contains("Delete the files 'build.gradle.kts'")
            solution.contains("in directory '${testDirectory}'")
        }
    }

    def "does not warn when only one build file exists: #buildFileName"() {
        given:
        file(buildFileName) << "// This is the only build file"

        when:
        succeeds('help')

        then:
        collectedProblems.empty

        where:
        buildFileName << ['build.gradle', 'build.gradle.kts', 'build.gradle.dcl']
    }

    def "warns about all ignored files when three build files exist"() {
        given:
        file("build.gradle") << """
            // from groovy
        """
        file("build.gradle.kts") << """
            // from kotlin
        """
        file("build.gradle.dcl") << """
            // from dcl
        """

        when:
        succeeds('help')

        then:
        verifyAll(receivedProblem) {
            definition.id.fqid == 'scripts:multiple-scripts'
            definition.id.displayName == 'Multiple scripts'
            contextualLabel == "Multiple build script files were found in directory '${testDirectory}'"
            details.contains("Selected 'build.gradle'")
            details.contains("'build.gradle.kts'")
            details.contains("'build.gradle.dcl'")
            solutions.size() == 1
            def solution = solutions[0]
            solution.contains("Delete the files")
            solution.contains("'build.gradle.kts'")
            solution.contains("'build.gradle.dcl'")
        }
    }

    def "uses the selected build file (Groovy over Kotlin)"() {
        given:
        file("build.gradle") << """
            task checkName {
                doLast {
                    println "From Groovy"
                }
            }
        """
        file("build.gradle.kts") << """
            tasks.register("checkName") {
                doLast {
                    println("From Kotlin")
                }
            }
        """

        when:
        succeeds('checkName')

        then:
        outputContains('From Groovy')

        and:
        // Still reports the problem
        receivedProblem.definition.id.fqid == 'scripts:multiple-scripts'
    }

    def "warns in composite build when included build has multiple build files"() {
        given:
        // Main build
        settingsFile << """
            rootProject.name = 'main-build'
            includeBuild 'included'
        """

        // Included build with multiple build files
        file("included/settings.gradle") << """
            rootProject.name = 'included-build'
        """
        file("included/build.gradle") << """
            // groovy
        """
        file("included/build.gradle.kts") << """
            // kotlin
        """

        when:
        succeeds('help')

        then:
        // Should have a problem for the included build
        verifyAll(receivedProblem) {
            definition.id.fqid == 'scripts:multiple-scripts'
            definition.id.displayName == 'Multiple scripts'
            contextualLabel.contains("Multiple build script files were found in directory")
            contextualLabel.contains("included")
            details.contains("Selected 'build.gradle'")
            details.contains("ignoring 'build.gradle.kts'")
        }
    }
}
