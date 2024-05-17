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

package org.gradle.configurationcache

class ConfigurationCacheProjectLayoutIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "buildDirectory #desc derived from task provider is compatible"() {
        given:
        buildFile """
            abstract class Producer extends DefaultTask {

                @OutputFile
                abstract RegularFileProperty getDirName()

                @TaskAction
                def produce() {
                    dirName.get().asFile.text = 'dir'
                }
            }

            abstract class Consumer extends DefaultTask {

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                def consume() {
                    outputFile.get().asFile.write('42')
                }
            }

            def computeDirName = tasks.register('producer', Producer) {
                dirName = layout.buildDirectory.file('dirName.txt')
            }

            tasks.register('answer', Consumer) {
                // TODO:configuration should this `dependsOn` be necessary here?
                dependsOn 'producer'

                Provider<String> computedDirName = computeDirName.flatMap { it.dirName }.map { it.asFile.text }
                def file = layout.buildDirectory.$provider
                outputFile = file
                doLast {
                    println(file.get().asFile.text)
                }
            }
        """

        when:
        configurationCacheRun 'answer'

        then:
        '42' == file('build/dir/answer.txt').text
        outputContains '42'

        where:
        desc   | provider
        'dir'  | 'dir(computedDirName).map { it.file("answer.txt") }'
        'file' | 'file(computedDirName.map { "$it/answer.txt" })'
    }
}
