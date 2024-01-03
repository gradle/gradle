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

package org.gradle.configurationcache


import org.gradle.api.tasks.TasksWithInputsAndOutputs

class ConfigurationCacheTaskWiringIntegrationTest extends AbstractConfigurationCacheIntegrationTest implements TasksWithInputsAndOutputs {
    def "task input property can consume the mapped output of another task"() {
        taskTypeWithInputFileProperty()
        taskTypeWithIntInputProperty()

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
        def configurationCache = newConfigurationCacheFixture()

        when:
        input.text = "12"
        configurationCacheRun(":transformer")

        then:
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "24"

        when:
        input.text = "4"
        configurationCacheRun(":transformer")

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "16"

        when:
        input.text = "10"
        configurationCacheRun(":transformer")

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "22"

        when:
        configurationCacheRun(":transformer")

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksSkipped(":producer", ":transformer")
    }

    def "task input property can consume the flat mapped output of another task"() {
        taskTypeWithInputFileProperty()
        taskTypeWithIntInputProperty()

        buildFile << """
            def producer = tasks.register('producer', InputFileTask) {
                inFile = file("in.txt")
                outFile = layout.buildDirectory.file("out.txt")
            }
            task transformer(type: InputTask) {
                inValue = producer.flatMap { t -> t.outFile.map { f -> f.asFile.text as Integer } }.map { i -> i + 2 }
                outFile = file("out.txt")
            }
        """
        def input = file("in.txt")
        def output = file("out.txt")
        def configurationCache = newConfigurationCacheFixture()

        when:
        input.text = "12"
        configurationCacheRun(":transformer")

        then:
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "24"

        when:
        input.text = "4"
        configurationCacheRun(":transformer")

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "16"

        when:
        input.text = "10"
        configurationCacheRun(":transformer")

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "22"

        when:
        configurationCacheRun(":transformer")

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksSkipped(":producer", ":transformer")
    }

    def "task input property can consume the mapped output of another task connected via project property with #description"() {
        taskTypeWithInputFileProperty()
        taskTypeWithIntInputProperty()

        buildFile << """
            def output = objects.property(Integer)
            output.with {
                $propertyConfig
            }
            task producer(type: InputFileTask) {
                inFile = file("in.txt")
                outFile = layout.buildDirectory.file("out.txt")
            }
            output.set(producer.outFile.map { f -> f.asFile.text as Integer }.map { i -> i + 2 })
            task transformer(type: InputTask) {
                inValue = output
                outFile = file("out.txt")
            }
        """
        def input = file("in.txt")
        def output = file("out.txt")
        def configurationCache = newConfigurationCacheFixture()

        when:
        input.text = "12"
        configurationCacheRun(":transformer")

        then:
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "24"

        when:
        input.text = "4"
        configurationCacheRun(":transformer")

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "16"

        when:
        input.text = "10"
        configurationCacheRun(":transformer")

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "22"

        when:
        configurationCacheRun(":transformer")

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksSkipped(":producer", ":transformer")

        where:
        description            | propertyConfig
        "default behaviour"    | ""
        "finalize on read"     | "it.finalizeValueOnRead()"
        "disallow unsafe read" | "it.disallowUnsafeRead()"
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
        def configurationCache = newConfigurationCacheFixture()

        when:
        input.text = "12"
        configurationCacheRun(":transformer")

        then:
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "22,24"

        when:
        input.text = "4"
        configurationCacheRun(":transformer")

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "14,16"

        when:
        input.text = "10"
        configurationCacheRun(":transformer")

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "20,22"

        when:
        configurationCacheRun(":transformer")

        then:
        configurationCache.assertStateLoaded()
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
        def configurationCache = newConfigurationCacheFixture()

        when:
        input.text = "12"
        configurationCacheRun(":transformer")

        then:
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "a=22,b=24"

        when:
        input.text = "4"
        configurationCacheRun(":transformer")

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "a=14,b=16"

        when:
        input.text = "10"
        configurationCacheRun(":transformer")

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecutedAndNotSkipped(":producer", ":transformer")
        output.text == "a=20,b=22"

        when:
        configurationCacheRun(":transformer")

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksSkipped(":producer", ":transformer")
    }
}
