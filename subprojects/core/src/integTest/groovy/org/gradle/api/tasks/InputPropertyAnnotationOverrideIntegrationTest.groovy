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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class InputPropertyAnnotationOverrideIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            class BaseTask extends DefaultTask {
                @OutputFile File output
                @TaskAction void run() {
                    output.text = "done"
                }
            }

            task custom(type: CustomTask) {
                output = new File(buildDir, "output")
            }
        """
        file("inputs/input").text = "initial"
    }

    def "can override @Internal with @#inputType.simpleName"() {
        buildFile << """
            class InternalBaseTask extends BaseTask {
                @Internal def input
            }
            class CustomTask extends InternalBaseTask {
                @${inputType.name} def input
            }
            custom {
                def layout = project.layout
                input = ${inputValue}
            }
        """
        when:
        succeeds("custom")
        then:
        file("build/output").text == "done"
        result.assertTasksExecuted(":custom")
        when:
        file("inputs/input").text = "new"
        succeeds("custom")
        then:
        result.assertTasksExecuted(":custom")

        where:
        inputType      | inputValue
        InputFile      | 'file("inputs/input")'
        InputDirectory | 'file("inputs")'
        InputFiles     | 'files("inputs")'
        Input          | '{ layout.projectDirectory.file("inputs/input").asFile.text }'
    }


    def "can override @#inputType.simpleName with @Internal"() {
        buildFile << """
            class InputBaseTask extends BaseTask {
                @${inputType.name} def input
            }
            class CustomTask extends InputBaseTask {
                @Internal def input
            }
            custom {
                input = ${inputValue}
            }
        """
        when:
        succeeds("custom")
        then:
        file("build/output").text == "done"
        result.assertTasksExecuted(":custom")

        when:
        file("inputs/input").text = "new"
        succeeds("custom")
        then:
        result.assertTasksSkipped(":custom")

        where:
        inputType      | inputValue
        InputFile      | 'file("inputs/input")'
        InputDirectory | 'file("inputs")'
        InputFiles     | 'files("inputs")'
        Input          | '{ file("inputs/input").text }'
    }
}
