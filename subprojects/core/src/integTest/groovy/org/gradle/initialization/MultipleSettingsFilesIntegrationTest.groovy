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

package org.gradle.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.scripts.ScriptingLanguages

class MultipleSettingsFilesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()
    }


    /**
     * If this test breaks, it means a new scripting language has been added.
     * It's important that this test covers all accepted extensions.
     *
     * Please update the tests in this file, and add the new extension to the list below.
     */
    def "list of extensions are what we expect"() {
        ScriptingLanguages.all().collect {it.extension} == [
            ".gradle",
            ".gradle.kts",
            ".gradle.dcl"
        ]
    }

    def "warns when multiple settings files exist in the same directory"() {
        given:
        // Create multiple settings files
        file("settings.gradle") << """
            // This is the selected settings file
        """
        file("settings.gradle.kts") << """
            // This file exists but should be ignored
        """
        file("settings.gradle.dcl") << """
            // This file exists but should be ignored
        """

        when:
        succeeds('help')

        then:
        // Verify that a problem was reported
        verifyAll(receivedProblem) {
            definition.id.fqid == 'scripts:multiple-scripts'
            definition.id.displayName == 'Multiple scripts'
            contextualLabel == "Multiple settings script files were found in directory '${testDirectory}'"
            details.contains("Selected 'settings.gradle'")
            details.contains("ignoring 'settings.gradle.kts'")
            solutions.size() == 1
            def solution = solutions[0]
            solution.contains("Delete the files 'settings.gradle.kts'")
            solution.contains("in directory '${testDirectory}'")
        }
    }

    def "does not warn when only one settings file exists: #settingsFile"() {
        given:
        file(settingsFile) << "// This is the only settings file"

        when:
        succeeds('help')

        then:
        collectedProblems.empty

        where:
        settingsFile << ['settings.gradle', 'settings.gradle.kts', 'settings.gradle.dcl']
    }

    def "warns about all ignored files when three settings files exist"() {
        given:
        file("settings.gradle") << """
            rootProject.name = 'from-groovy'
        """
        file("settings.gradle.kts") << """
            rootProject.name = "from-kotlin"
        """
        file("settings.gradle.dcl") << """
            rootProject {
                name = "from-dcl"
            }
        """

        when:
        succeeds('help')

        then:
        verifyAll(receivedProblem) {
            definition.id.fqid == 'scripts:multiple-scripts'
            definition.id.displayName == 'Multiple scripts'
            contextualLabel == "Multiple settings script files were found in directory '${testDirectory}'"
            details.contains("Selected 'settings.gradle'")
            details.contains("'settings.gradle.kts'")
            details.contains("'settings.gradle.dcl'")
            solutions.size() == 1
            def solution = solutions[0]
            solution.contains("Delete the files")
            solution.contains("'settings.gradle.kts'")
            solution.contains("'settings.gradle.dcl'")
        }
    }

    def "uses the selected settings file (Groovy over Kotlin)"() {
        given:
        file("settings.gradle") << """
            rootProject.name = 'from-groovy'
        """
        file("settings.gradle.kts") << """
            rootProject.name = "from-kotlin"
        """
        buildFile << """
            task checkName {
                def projectName = rootProject.name
                doLast {
                    println "Root project name: \${projectName}"
                }
            }
        """

        when:
        succeeds('checkName')

        then:
        outputContains('Root project name: from-groovy')

        and:
        // Still reports the problem
        receivedProblem.definition.id.fqid == 'scripts:multiple-scripts'
    }

    def "warns in composite build when included build has multiple settings files"() {
        given:
        // Main build
        settingsFile << """
            rootProject.name = 'main-build'
            includeBuild 'included'
        """

        // Included build with multiple settings files
        file("included/settings.gradle") << """
            rootProject.name = 'included-groovy'
        """
        file("included/settings.gradle.kts") << """
            rootProject.name = "included-kotlin"
        """
        file("included/build.gradle") << """
            group = 'org.example'
            version = '1.0'
        """

        when:
        succeeds('help')

        then:
        // Should have a problem for the included build
        verifyAll(receivedProblem) {
            definition.id.fqid == 'scripts:multiple-scripts'
            definition.id.displayName == 'Multiple scripts'
            contextualLabel.contains("Multiple settings script files were found in directory")
            contextualLabel.contains("included")
            details.contains("Selected 'settings.gradle'")
            details.contains("ignoring 'settings.gradle.kts'")
        }
    }

    def "warns even when only using Kotlin settings file in practice"() {
        given:
        // Create settings.gradle but make it invalid
        file("settings.gradle") << """
            // This file exists but we'll verify Groovy is selected
            rootProject.name = 'from-groovy'
        """
        file("settings.gradle.kts") << """
            rootProject.name = "from-kotlin"
        """

        when:
        succeeds('help')

        then:
        // Even though Kotlin file might work fine, if both exist, a warning should be shown
        verifyAll(receivedProblem) {
            definition.id.fqid == 'scripts:multiple-scripts'
            details.contains("Selected 'settings.gradle'")
        }
    }
}
