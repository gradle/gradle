/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution


import org.gradle.api.tasks.TasksWithInputsAndOutputs

class InstantExecutionTaskWiringIntegrationTest extends AbstractInstantExecutionIntegrationTest implements TasksWithInputsAndOutputs {
    def "task input property can consume the mapped output of another task"() {
        taskTypeWithInputFileProperty()
        taskTypeWithInputProperty()

        buildFile << """
            task producer(type: InputFileTask) {
                inFile = file("in.txt")
                outFile = layout.buildDirectory.file("out.txt")
            }
            task transformer(type: InputTask) {
                inValue = producer.outFile.map { f -> f.asFile.text as Integer }.map { i -> i + 2 }
                outFile = file("out.txt")
            }
        """
        def input = file("in.txt")
        def output = file("out.txt")
        def instantExecution = newInstantExecutionFixture()

        when:
        input.text = "12"
        instantRun(":transformer")

        then:
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "24"

        when:
        input.text = "4"
        instantRun(":transformer")

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "16"

        when:
        input.text = "10"
        instantRun(":transformer")

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "22"

        when:
        instantRun(":transformer")

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksSkipped(":producer", ":transformer")
    }

    def "task input collection property can consume the mapped output of another task"() {
        taskTypeWithInputFileProperty()
        taskTypeWithInputListProperty()

        buildFile << """
            task producer(type: InputFileTask) {
                inFile = file("in.txt")
                outFile = layout.buildDirectory.file("out.txt")
            }
            task transformer(type: InputTask) {
                inValue = producer.outFile.map { f -> f.asFile.text as Integer }.map { i -> [i, i + 2] }
                outFile = file("out.txt")
            }
        """
        def input = file("in.txt")
        def output = file("out.txt")
        def instantExecution = newInstantExecutionFixture()

        when:
        input.text = "12"
        instantRun(":transformer")

        then:
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "22,24"

        when:
        input.text = "4"
        instantRun(":transformer")

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "14,16"

        when:
        input.text = "10"
        instantRun(":transformer")

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "20,22"

        when:
        instantRun(":transformer")

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksSkipped(":producer", ":transformer")
    }

    def "task input map property can consume the mapped output of another task"() {
        taskTypeWithInputFileProperty()
        taskTypeWithInputMapProperty()

        buildFile << """
            task producer(type: InputFileTask) {
                inFile = file("in.txt")
                outFile = layout.buildDirectory.file("out.txt")
            }
            task transformer(type: InputTask) {
                inValue = producer.outFile.map { f -> f.asFile.text as Integer }.map { i -> [a: i, b: i + 2] }
                outFile = file("out.txt")
            }
        """
        def input = file("in.txt")
        def output = file("out.txt")
        def instantExecution = newInstantExecutionFixture()

        when:
        input.text = "12"
        instantRun(":transformer")

        then:
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "a=22,b=24"

        when:
        input.text = "4"
        instantRun(":transformer")

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "a=14,b=16"

        when:
        input.text = "10"
        instantRun(":transformer")

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "a=20,b=22"

        when:
        instantRun(":transformer")

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksSkipped(":producer", ":transformer")
    }
}
