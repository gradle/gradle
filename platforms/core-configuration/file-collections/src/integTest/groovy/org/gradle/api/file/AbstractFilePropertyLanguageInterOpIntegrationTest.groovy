/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.file

import org.gradle.api.internal.provider.AbstractLanguageInterOpIntegrationTest
import org.gradle.api.tasks.TasksWithInputsAndOutputs
import org.junit.Assume

abstract class AbstractFilePropertyLanguageInterOpIntegrationTest extends AbstractLanguageInterOpIntegrationTest implements TasksWithInputsAndOutputs {
    abstract void pluginDefinesTask()

    abstract void pluginDefinesTaskWithNestedBean()

    boolean nestedGetterIsFinal() {
        false
    }

    def "can connect task output file property to task input"() {
        pluginDefinesTask()
        taskTypeWithInputFileProperty()
        buildFile << """
            apply plugin: SomePlugin
            task consumer(type: InputFileTask) {
                inFile = producer.outFile
                outFile = file("out.txt")
            }
        """

        when:
        run("consumer")

        then:
        result.ignoreBuildSrc.assertTasksExecuted(":producer", ":consumer")
        file("out.txt").text == "content"
    }

    def "can connect task nested output file property to task input"() {
        Assume.assumeTrue(!nestedGetterIsFinal()) // currently not supported

        pluginDefinesTaskWithNestedBean()
        taskTypeWithInputFileProperty()
        buildFile << """
            apply plugin: SomePlugin
            task consumer(type: InputFileTask) {
                inFile = producer.params.outFile
                outFile = file("out.txt")
            }
        """

        when:
        run("consumer")

        then:
        result.ignoreBuildSrc.assertTasksExecuted(":producer", ":consumer")
        file("out.txt").text == "content"
    }
}
