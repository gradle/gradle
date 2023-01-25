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

package org.gradle.api.file

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ConfigurableFileCollectionAssignmentIntegrationTest extends AbstractIntegrationSpec {

    def "test Groovy FileCollection types assignment for #description"() {
        given:
        def buildFileDefinition = """
            abstract class MyTask extends DefaultTask {
                @Internal
                {inputDeclaration}
                @TaskAction
                void run() {
                    println("Result: " + input.files.collect { it.name })
                }
            }
            tasks.register("myTask", MyTask) {
                input $operation {inputValue}
            }
        """

        when:
        buildFile.text = buildFileDefinition
            .replace("{inputDeclaration}", "$eagerInputType input = project.files() as $eagerInputType")
            .replace("{inputValue}", inputValue)

        then:
        run("myTask")
        outputContains(eagerResult)

        when:
        buildFile.text = buildFileDefinition
            .replace("{inputDeclaration}", "abstract $lazyInputType getInput()")
            .replace("{inputValue}", inputValue)

        then:
        run("myTask")
        outputContains(lazyResult)

        where:
        description                       | operation | eagerInputType   | lazyInputType                | inputValue                         | eagerResult       | lazyResult
        "FileCollection = FileCollection" | "="       | "FileCollection" | "ConfigurableFileCollection" | 'files("a.txt")'                   | 'Result: [a.txt]' | 'Result: [a.txt]'
        "FileCollection = FileCollection" | "="       | "FileCollection" | "ConfigurableFileCollection" | 'files("a.txt") as FileCollection' | 'Result: [a.txt]' | 'Result: [a.txt]'
    }

    def "test Kotlin FileCollection types assignment for #description"() {
        given:
        file("gradle.properties") << "\nsystemProp.org.gradle.unsafe.kotlin.assignment=true\n"
        def buildFileDefinition = """
            abstract class MyTask : DefaultTask() {
                @get:Internal
                {inputDeclaration}
                @TaskAction
                fun run() {
                    println("Result: " + input.files.map { it.name })
                }
            }
            tasks.register<MyTask>("myTask") {
                input $operation {inputValue}
            }
        """

        when:
        buildKotlinFile.text = buildFileDefinition
            .replace("{inputDeclaration}", "var input: $eagerInputType = project.files()")
            .replace("{inputValue}", inputValue)

        then:
        run("myTask")
        outputContains(eagerResult)

        when:
        buildKotlinFile.text = buildFileDefinition
            .replace("{inputDeclaration}", "abstract val input: $lazyInputType")
            .replace("{inputValue}", inputValue)

        then:
        run("myTask")
        outputContains(lazyResult)

        where:
        description                       | operation | eagerInputType   | lazyInputType                | inputValue                         | eagerResult       | lazyResult
        "FileCollection = FileCollection" | "="       | "FileCollection" | "ConfigurableFileCollection" | 'files("a.txt")'                   | 'Result: [a.txt]' | 'Result: [a.txt]'
        "FileCollection = FileCollection" | "="       | "FileCollection" | "ConfigurableFileCollection" | 'files("a.txt") as FileCollection' | 'Result: [a.txt]' | 'Result: [a.txt]'
    }
}
