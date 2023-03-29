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

import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE

class TestInputAnnotationFailuresIntegrationTest extends AbstractIntegrationSpec {
    def "using @Input annotation on #elementType file elements fails validation with helpful error message"() {
        given:
        buildFile << """
            $commonJavaPart

            abstract class MyTask extends DefaultTask {

                @Input
                $elementInitialization

                @TaskAction
                void action() {
                }
            }

            tasks.register('myTask', MyTask)
        """

        expect:
        fails 'myTask'
        result.assertHasErrorOutput("A problem was found with the configuration of task ':myTask' (type 'MyTask').")
        result.assertHasErrorOutput("- Type 'MyTask' property '$elementName' has @Input annotation used on property of type '$elementType'.")
        result.assertHasErrorOutput("Reason: A property of type '$elementType' annotated with @Input cannot determine how to interpret the file.")
        result.assertTaskNotExecuted('myTask')
        failure.assertHasResolutions(
            "Annotate with @InputFile for regular files.",
            "Annotate with @InputFiles for collections of files.",
            "If you want to track the path, return File.absolutePath as a String and keep @Input.",
            STACKTRACE_MESSAGE,
            INFO_DEBUG,
            SCAN,
            GET_HELP)


        where:
        elementType           | elementName | elementInitialization
        'File'                | 'myField'   | "File myField = project.layout.projectDirectory.file('myFile.txt').getAsFile()"
        'RegularFile'         | 'myField'   | "RegularFile myField = project.layout.projectDirectory.file('myFile.txt')"
        'RegularFileProperty' | 'myProp'    | 'abstract RegularFileProperty getMyProp()'
    }

    def "using @InputFile annotation on a private property produces helpful error message"() {
        given:
        buildFile << """
            $commonJavaPart

            abstract class MyTask extends DefaultTask {
                @InputFile
                private RegularFileProperty inputFile

                @TaskAction
                void action() {
                }
            }

            tasks.register('myTask', MyTask)
        """

        expect:
        fails 'myTask'
        result.assertHasErrorOutput("A problem was found with the configuration of task ':myTask' (type 'MyTask').")
        result.assertHasErrorOutput("- Type 'MyTask' field 'inputFile' without corresponding getter has been annotated with @InputFile.")
        result.assertHasErrorOutput("Reason: Annotations on fields are only used if there's a corresponding getter for the field.")
        result.assertTaskNotExecuted('myTask')
        failure.assertHasResolutions(
            "Add a getter for field 'inputFile'.",
            "Remove the annotations on 'inputFile'.",
            STACKTRACE_MESSAGE,
            INFO_DEBUG,
            SCAN,
            GET_HELP)
    }

    def "using @Input annotation on #elementType directory elements fails validation with helpful error message"() {
        given:
        buildFile << """
            $commonJavaPart

            abstract class MyTask extends DefaultTask {
                @Input
                $elementInitialization

                @TaskAction
                void action() {
                }
            }

            tasks.register('myTask', MyTask)
        """

        expect:
        fails 'myTask'
        result.assertHasErrorOutput("A problem was found with the configuration of task ':myTask' (type 'MyTask').")
        result.assertHasErrorOutput("- Type 'MyTask' property '$elementName' has @Input annotation used on property of type '$elementType'.")
        result.assertHasErrorOutput("Reason: A property of type '$elementType' annotated with @Input cannot determine how to interpret the file.")
        result.assertTaskNotExecuted('myTask')
        failure.assertHasResolutions(
            "Annotate with @InputDirectory for directories.",
            STACKTRACE_MESSAGE,
            INFO_DEBUG,
            SCAN,
            GET_HELP)


        where:
        elementType         | elementName | elementInitialization
        'Directory'         | 'myField'   | "Directory myField = project.layout.projectDirectory.dir('myDir')"
        "DirectoryProperty" | 'myProp'    | "abstract DirectoryProperty getMyProp()"
    }

    def getCommonJavaPart(){
        """import groovy.transform.CompileStatic

            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}
"""
    }
    def "using @Input annotation on #propertyType file properties succeeds"() {
        given:
        buildFile << """
            $commonJavaPart

            abstract class MyTask extends DefaultTask {
                @Inject
                public abstract ObjectFactory getObjectFactory()

                @Input
                $propertyInitialization

                @TaskAction
                void action() {
                }
            }

            tasks.register('myTask', MyTask) {
                $propertyAssignment
            }
        """

        expect:
        succeeds 'myTask'

        where:
        propertyType     | propertyInitialization                                                  | propertyAssignment
        "Property<File>" | "final Property<File> myProp = getObjectFactory().property(File.class)" | "myProp.set(project.layout.projectDirectory.file('myFile').getAsFile())"
    }
}
