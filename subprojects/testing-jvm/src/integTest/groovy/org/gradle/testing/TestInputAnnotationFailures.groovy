/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TestInputAnnotationFailures extends AbstractIntegrationSpec {
    def "misconfigured @Input annotation fails with outputs.upToDateWhen() with helpful error message"() {
        given:
        buildFile << """
            import groovy.transform.CompileStatic

            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            @CacheableTask
            @CompileStatic
            abstract class MyTask extends DefaultTask {
                @Input
                abstract RegularFileProperty getMyFile()

                @TaskAction
                void action() {
                    logger.warn("Input file: {}", myFile.getAsFile().get().absolutePath)
                }
            }

            tasks.register('myTask', MyTask) {
                outputs.upToDateWhen { false }
                myFile = project.layout.projectDirectory.file('myFile.txt')
            }
        """

        expect:
        fails 'myTask'
        result.assertHasErrorOutput("Cannot fingerprint input property 'myFile'")
        result.assertHasErrorOutput("This property might have to use @InputFile, or a related file-based input annotation, instead of @Input")
    }

    def "misconfigured @Input annotation succeeds if no upToDate check done"() {
        given:
        buildFile << """
            import groovy.transform.CompileStatic

            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            @CacheableTask
            @CompileStatic
            abstract class MyTask extends DefaultTask {
                @Input
                abstract RegularFileProperty getMyFile()

                @TaskAction
                void action() {
                    logger.warn("Input file: {}", myFile.getAsFile().get().absolutePath)
                }
            }

            tasks.register('myTask', MyTask) {
                myFile = project.layout.projectDirectory.file('myFile.txt')
            }
        """

        expect:
        succeeds 'myTask'
    }
}
