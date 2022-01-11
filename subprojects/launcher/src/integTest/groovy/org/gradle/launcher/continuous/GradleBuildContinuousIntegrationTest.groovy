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

package org.gradle.launcher.continuous

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest

class GradleBuildContinuousIntegrationTest extends AbstractContinuousIntegrationTest {
    def setup() {
        file("gradle-build/inputs/input.txt").text = "first"
        file("gradle-build/settings.gradle") << """
            rootProject.name = "gradle-build"
        """
        file("gradle-build/build.gradle") << """
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

        buildFile << """
            task gradleBuild(type: GradleBuild) {
                dir = file("gradle-build")
                tasks = [ "someTask" ]
            }
        """
    }

    def "will rebuild on input change for GradleBuild task"() {
        def outputFile = file("gradle-build/build/output.txt")

        when:
        succeeds("gradleBuild")
        then:
        outputFile.text == "first"

        when:
        file("gradle-build/inputs/input.txt").text = "second"
        then:
        buildTriggeredAndSucceeded()
        outputFile.text == "second"
    }
}
