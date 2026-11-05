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
    def "ConfigurableFileCollection self-referencing assignment '#description' uses previous value in Groovy DSL"() {
        buildFile """
            abstract class MyTask extends DefaultTask {
                @Internal
                abstract ConfigurableFileCollection getInput()

                @TaskAction
                void run() {
                    println("Result: " + input.files.collect { it.name })
                }
            }

            tasks.register("myTask", MyTask) {
                input.from(files("a", "b"))
                $statement
            }
        """

        when:
        succeeds "myTask"

        then:
        outputContains("Result: $result")

        where:
        description      | statement                                | result
        "a += b"         | 'input += files("c")'                    | "[a, b, c]"
        "a = a + b"      | 'input = input + files("c")'            | "[a, b, c]"
        "a = a.plus(b)"  | 'input = input.plus(files("c"))'        | "[a, b, c]"
        "a -= b"         | 'input -= files("a")'                   | "[b]"
        "a = a - b"      | 'input = input - files("a")'            | "[b]"
        "a = a.minus(b)" | 'input = input.minus(files("a"))'       | "[b]"
    }
}
