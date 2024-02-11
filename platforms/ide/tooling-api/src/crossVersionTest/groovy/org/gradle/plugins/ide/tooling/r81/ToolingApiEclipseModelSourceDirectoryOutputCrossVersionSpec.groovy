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

package org.gradle.plugins.ide.tooling.r81

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.eclipse.EclipseProject

@ToolingApiVersion('>=8.1')
@TargetGradleVersion(">=8.1")
class ToolingApiEclipseModelSourceDirectoryOutputCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        buildFile << """
            plugins {
                id "java"
                id "eclipse"
            }
        """
        file("src/main/java").mkdirs()
        file("src/main/resources").mkdirs()
        file("src/test/java").mkdirs()
        file("src/test/resources").mkdirs()
    }

    def "can configure src output with 'baseSourceOutputDir'"() {
        setup:
        buildFile << """
            eclipse {
                classpath {
                    baseSourceOutputDir = file('custom-output')
                }
            }
        """

        when:
        def model = loadToolingModel EclipseProject

        then:

        model.sourceDirectories.size() == 4
        model.sourceDirectories[0].output == 'custom-output/main'
        model.sourceDirectories[1].output == 'custom-output/main'
        model.sourceDirectories[2].output == 'custom-output/test'
        model.sourceDirectories[3].output == 'custom-output/test'
    }

    def "can configure src output with default value"() {

        when:
        def model = loadToolingModel EclipseProject

        then:

        model.sourceDirectories.size() == 4
        model.sourceDirectories[0].output == 'bin/main'
        model.sourceDirectories[1].output == 'bin/main'
        model.sourceDirectories[2].output == 'bin/test'
        model.sourceDirectories[3].output == 'bin/test'
    }
}
