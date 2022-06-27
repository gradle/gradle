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
    def "using @Input annotation on #fieldType fields with upToDate check fails with helpful error message"() {
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
                $fieldInitialization

                @TaskAction
                void action() {
                    logger.warn("Input file: {}", $fieldRead)
                }
            }

            tasks.register('myTask', MyTask) {
                outputs.upToDateWhen { false }
            }
        """

        expect:
        fails 'myTask'
        result.assertHasErrorOutput("Cannot fingerprint input property 'myField'")
        result.assertHasErrorOutput("This property might have to use @InputFile, or a related file-based input annotation, instead of @Input")

        where:
        fieldType     | fieldInitialization                                                         | fieldRead
        'RegularFile' | "RegularFile myField = project.layout.projectDirectory.file('myFile.txt')"  | 'myField.getAsFile().absolutePath'
        'Directory'   | "Directory myField = project.layout.projectDirectory.dir('myDir')"          | 'myField.getAsFile().absolutePath'
    }

    def "using @Input annotation on #propertyType fields with upToDate check fails with helpful error message"() {
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
                $propertyInitialization

                @TaskAction
                void action() {
                    logger.warn("Input file: {}", $propertyRead)
                }
            }

            tasks.register('myTask', MyTask) {
                $propertyAssignment
                outputs.upToDateWhen { false }
            }
        """

        expect:
        fails 'myTask'
        result.assertHasErrorOutput("Cannot fingerprint input property 'myProp'")
        result.assertHasErrorOutput("This property might have to use @InputFile, or a related file-based input annotation, instead of @Input")

        where:
        propertyType            | propertyInitialization                        | propertyAssignment                                            | propertyRead
        "RegularFileProperty"   | "abstract RegularFileProperty getMyProp()"    | "myProp = project.layout.projectDirectory.file('myFile.txt')" | "myProp.getAsFile().get().absolutePath"
        "DirectoryProperty"     | "abstract DirectoryProperty getMyProp()"      | "myProp = project.layout.projectDirectory"                    | "myProp.getAsFile().get().absolutePath"
    }

    def "misconfigured @Input annotation on RegularFileProperty succeeds if no upToDate check done"() {
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
