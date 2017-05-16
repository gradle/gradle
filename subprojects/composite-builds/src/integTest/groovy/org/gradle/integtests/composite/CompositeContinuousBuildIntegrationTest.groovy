/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.composite

import org.gradle.launcher.continuous.Java7RequiringContinuousIntegrationTest
import spock.lang.Ignore

class CompositeContinuousBuildIntegrationTest extends Java7RequiringContinuousIntegrationTest {
    def setup() {
        file("included/inputs/input.txt").text = "first"
        file("included/settings.gradle") << """
            rootProject.name = "included"
        """
        file("included/build.gradle") << """
            task someTask {
                def inputFile = file("inputs/input.txt")
                def outputFile = file("build/output.txt")
                inputs.file inputFile
                outputs.file outputFile
                doLast {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = inputFile.text
                }
            }
        """

        settingsFile << """
            rootProject.name = "root"
            includeBuild "included"
        """

        buildFile << """
            task composite {
                dependsOn gradle.includedBuild("included").task(":someTask")
            }
        """
    }

    @Ignore // fails to execute included task when triggered
    def "can watch inputs to composite build tasks"() {
        def outputFile = file("included/build/output.txt")

        when:
        succeeds("composite")
        then:
        outputFile.text == "first"

        when:
        file("included/inputs/input.txt").text = "second"
        then:
        succeeds()
        outputFile.text == "second"
    }
}
