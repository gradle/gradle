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

package org.gradle.internal.watch


import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class UpToDateCheckWithVfsIntegrationTest extends AbstractIntegrationSpec {

    def "up-to-date check with VFS"() {
        given:
        buildFile << """
            abstract class TaskWithInputs extends DefaultTask {
                @InputFile
                @PathSensitive(PathSensitivity.RELATIVE)
                abstract RegularFileProperty getSource()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void doSomething() {
                    outputFile.asFile.get().text = source.asFile.get().text
                }
            }

            tasks.register("foo", TaskWithInputs) {
                source = file("source.txt")
                outputFile = file("output.txt")
            }
        """

        when:
        file("source.txt") << "hello"

        then:
        run("foo")
        outputContains("Has unit of work ':foo' changed? true")

        when:
        file("source.txt") << "hello world"

        then:
        run("foo")
        outputContains("Has unit of work ':foo' changed? true")

        when:
        file("source.txt")

        then:
        run("foo")
        outputContains("Has unit of work ':foo' changed? false")
    }
}
