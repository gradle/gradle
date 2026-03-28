/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.provider


import spock.lang.Issue

class ConfigurableFileCollectionSelfReferencingIntegrationTest extends AbstractProviderOperatorIntegrationTest {

    @Issue("https://github.com/gradle/gradle/issues/32177")
    def "ConfigurableFileCollection subtraction assignment '#description' works correctly in Groovy DSL"() {
        given:
        file("a.txt").createFile()
        file("b.txt").createFile()
        file("c.txt").createFile()

        buildFile """
            abstract class MyTask extends DefaultTask {
                @Internal
                abstract ConfigurableFileCollection getInput()

                @TaskAction
                void run() {
                    println("Result: " + input.files.collect { it.name }.sort())
                }
            }

            tasks.register("myTask", MyTask) {
                input.from("a.txt", "b.txt")
                $statement
            }
        """

        when:
        succeeds "myTask"

        then:
        outputContains(expected)

        where:
        description                    | statement                                                          | expected
        "a -= b"                       | 'input -= files("b.txt")'                                          | 'Result: [a.txt]'
        "a = a - b"                    | 'input = input - files("b.txt")'                                   | 'Result: [a.txt]'
        "a = a.minus(b)"               | 'input = input.minus(files("b.txt"))'                              | 'Result: [a.txt]'
        "a = (a - b) + c"              | 'input = (input - files("b.txt")) + files("c.txt")'                | 'Result: [a.txt, c.txt]'
        "a = c + (a - b)"              | 'input = files("c.txt") + (input - files("b.txt"))'                | 'Result: [a.txt, c.txt]'
        "a = d - a (a on right side)"  | 'input = files("a.txt", "b.txt", "c.txt") - input'                 | 'Result: [c.txt]'
    }

    def "ConfigurableFileCollection addition assignment '#description' works correctly in Groovy DSL"() {
        given:
        file("a.txt").createFile()
        file("b.txt").createFile()

        buildFile """
            abstract class MyTask extends DefaultTask {
                @Internal
                abstract ConfigurableFileCollection getInput()

                @TaskAction
                void run() {
                    println("Result: " + input.files.collect { it.name }.sort())
                }
            }

            tasks.register("myTask", MyTask) {
                input.from("a.txt")
                $statement
            }
        """

        when:
        succeeds "myTask"

        then:
        outputContains("Result: [a.txt, b.txt]")

        where:
        description                    | statement
        "a += b"                       | 'input += files("b.txt")'
        "a = a + b"                    | 'input = input + files("b.txt")'
    }
}
