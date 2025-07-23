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

package org.gradle.api.internal.artifacts.configurations.state.reporting

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

class ConfigurationUsageFeedbackPluginIntegrationTest extends AbstractIntegrationSpec {
    def "can apply the plugin"() {
        given:
        settingsFile << """
            plugins {
                id 'org.gradle.configuration-usage-reporting'
            }
        """

        when:
        succeeds("tasks")

        then:
        def output = getDefaultReportFile()
        output.exists()
        output.text == "No configurations were created in this build."
    }

    def "plugin reports when no configurations were used incorrectly"() {
        given:
        settingsFile << """
            plugins {
                id 'org.gradle.configuration-usage-reporting'
            }
        """

        buildFile << """
            ${mavenCentralRepository()}

            configurations {
                dependencyScope("myConfig")
                resolvable("myResolvable") {
                    extendsFrom myConfig

                    attributes {
                        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL))
                    }
                }
            }

            dependencies {
                myConfig("org.junit.jupiter:junit-jupiter:5.7.0")
            }

            tasks.register("resolve") {
                inputs.files configurations.myResolvable

                doLast {
                    inputs.files.files.forEach { println(it) }
                }
            }
        """

        when:
        succeeds("resolve")

        then:
        def output = getDefaultReportFile()
        output.exists()
        output.text == "No configuration state was accessed incorrectly."
    }

    def "can configure the plugin"() {
        given:
        def customReportFilePath = "build/custom-file.txt"
        settingsFile << """
            plugins {
                id 'org.gradle.configuration-usage-reporting'
            }

            configurationUsageFeedback {
                reportFile = file("$customReportFilePath")
            }
        """

        when:
        succeeds("tasks")

        then:
        def output = file(customReportFilePath)
        output.exists()
        output.text == "No configurations were created in this build."

        def defaultOutput = getDefaultReportFile()
        !defaultOutput.exists()
    }

    def "reports on java-library"() {
        given:
        settingsFile << """
            plugins {
                id 'org.gradle.configuration-usage-reporting'
            }
        """

        buildFile << """
            plugins {
                id 'java-library'
            }
        """

        when:
        succeeds("build")

        then:
        def output = getDefaultReportFile()
        output.exists()
        output.text.contains("Project: root project")
        println(output.text)
    }

    def "reports all usage on java-library"() {
        given:
        settingsFile << """
            plugins {
                id 'org.gradle.configuration-usage-reporting'
            }

            configurationUsageFeedback {
                showAllUsage = true
            }
        """

        buildFile << """
            plugins {
                id 'java-library'
            }
        """

        when:
        succeeds("build")

        then:
        def output = getDefaultReportFile()
        output.exists()
        output.text.contains("Project: root project")
        println(output.text)
    }

    def "reports on java-library used incorrectly"() {
        given:
        settingsFile << """
            plugins {
                id 'org.gradle.configuration-usage-reporting'
            }
        """

        buildFile << """
            plugins {
                id 'java-library'
            }

            configurations.compileClasspath.markAsObserved("I saw you!")
        """

        when:
        succeeds("build")

        then:
        def output = getDefaultReportFile()
        output.exists()
        output.text.contains("Project: root project")
        println(output.text)
    }

    private TestFile getDefaultReportFile() {
        file("${ConfigurationUsageFeedbackPlugin.DEFAULT_REPORTS_DIR}/${ConfigurationUsageFeedbackPlugin.DEFAULT_REPORT_FILE}")
    }
}
