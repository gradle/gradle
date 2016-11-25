/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.internal.os.OperatingSystem

class CompositeBuildWarningContinuousBuildIntegrationTest extends Java7RequiringContinuousIntegrationTest {

    static final String WARNING_MESSAGE = "[composite-build] Warning: continuous build doesn't detect changes in included builds."

    def "No warning is shown for a non-composite build"() {
        given:
        singleProjectBuild("project")

        when:
        succeeds 'build'

        then:
        output.count(WARNING_MESSAGE) == 0
    }

    def "Warning is shown for a composite build"() {
        singleProjectBuild("project") {
            settingsFile << "includeBuild 'included'"
            file('included', 'settings.gradle').touch()
        }

        when:
        succeeds 'build'

        then:
        output.count(WARNING_MESSAGE) == 1
    }

    def "Warning is shown after changes detected"() {
        executer.withBuildJvmOpts("-Dorg.gradle.internal.filewatch.quietperiod=${OperatingSystem.current().isMacOsX() ? 2500L : 1000L}")
        singleProjectBuild("project") {
            buildFile << """
                task theTask {
                    inputs.dir "inputDir"
                    doLast {}
                }
            """
            settingsFile << "includeBuild 'included'"
            file('inputDir').mkdirs()
            file('included', 'settings.gradle').touch()
        }

        when:
        succeeds 'theTask'
        def firstWarning = output.count(WARNING_MESSAGE)

        file('inputDir', 'foo.txt') << 'bar'
        succeeds()

        then:
        firstWarning + output.count(WARNING_MESSAGE) == 2
    }
}
