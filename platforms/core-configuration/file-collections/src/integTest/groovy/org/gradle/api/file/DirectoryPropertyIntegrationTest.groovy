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

package org.gradle.api.file

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class DirectoryPropertyIntegrationTest extends AbstractIntegrationSpec {

    @Issue('https://github.com/gradle/gradle/issues/17533')
    def 'DirectoryProperty.file(#type) preserves task dependency'() {
        given:
        buildFile """
            abstract class Producer extends DefaultTask {
                @OutputDirectory abstract DirectoryProperty getOutDir()
                @TaskAction def producer() {
                    outDir.file('output').get().asFile.text = '42'
                }
            }

            abstract class Consumer extends DefaultTask {
                @InputFile abstract RegularFileProperty getInFile()
                @TaskAction def consumer() {}
            }

            def producer = tasks.register('producer', Producer) {
                outDir.set(layout.buildDirectory.dir('out'))
            }

            tasks.register('consumer', Consumer) {
                inFile.set(producer.$expression)
            }
        """

        when:
        succeeds 'consumer'

        then:
        executed ':producer', ':consumer'

        where:
        type                   | expression
        'value'                | 'get().outDir.file("output")'
        'provider'             | 'get().outDir.file(provider { "output" })'
        'flatMap { value }'    | 'flatMap { it.outDir.file("output") }'
        'flatMap { provider }' | 'flatMap { it.outDir.file(provider { "output" }) }'
    }
}
