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
    def "ConfigurableFileCollection shallow self-subtraction assignment '#description' throws meaningful error in Groovy DSL"() {
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
                $statement
            }
        """

        when:
        fails "myTask"

        then:
        failureCauseContains("ConfigurableFileCollection does not support '-=' operator or assignment of subtraction via '-' operator or a minus() method")

        where:
        description      | statement
        "a -= b"         | 'input -= files("a")'
        "a = a - b"      | 'input = input - files("a")'
        "a = a.minus(b)" | 'input = input.minus(files("a"))'
    }
}
