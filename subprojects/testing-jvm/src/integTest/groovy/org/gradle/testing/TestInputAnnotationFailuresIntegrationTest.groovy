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
import org.gradle.internal.execution.WorkValidationException

class TestInputAnnotationFailuresIntegrationTest extends AbstractIntegrationSpec {
    def "using @Input annotation on #elementType file elements fails validation with helpful error message"() {
        given:
        buildFile << """
            import groovy.transform.CompileStatic

            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            abstract class MyTask extends DefaultTask {
                @Inject
                public abstract ObjectFactory getObjectFactory()

                @Input
                $elementInitialization

                @TaskAction
                void action() {
                    logger.warn("Input file: {}", $elementRead)
                }
            }

            tasks.register('myTask', MyTask)
        """

        expect:
        fails 'myTask'

        where:
        elementType             | elementName   | elementInitialization                                                             | elementRead
        'File'                  | 'myField'     | "File myField = project.layout.projectDirectory.file('myFile.txt').getAsFile()"   | 'myField.absolutePath'
    }

    // This test should be removed in Gradle 8.0, as once the validation is restored to an error from the current warning, it will become an error
    def "using @Input annotation on #elementType file elements succeeds but produces validation with helpful error message"() {
        given:
        buildFile << """
            import groovy.transform.CompileStatic

            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            abstract class MyTask extends DefaultTask {
                @Inject
                public abstract ObjectFactory getObjectFactory()

                @Input
                $elementInitialization

                @TaskAction
                void action() {
                    logger.warn("Input file: {}", $elementRead)
                }
            }

            tasks.register('myTask', MyTask)
        """

        expect:
        executer.expectDeprecationWarning("Type 'MyTask' property '$elementName' has @Input annotation used on property of type '$elementType'. " +
                "Reason: A property of type '$elementType' annotated with @Input cannot determine how to interpret the file.")
        succeeds 'myTask'

        where:
        elementType             | elementName   | elementInitialization                                                             | elementRead
        'RegularFile'           | 'myField'     | "RegularFile myField = project.layout.projectDirectory.file('myFile.txt')"        | 'myField.getAsFile().absolutePath'
    }

    // This test should be removed in Gradle 8.0, as once the validation is restored to an error from the current warning, it will prevent the other problem from being detected
    def "using @Input annotation on #elementType file elements and not setting a value fails validation with helpful error message"() {
        given:
        buildFile << """
            import groovy.transform.CompileStatic

            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            abstract class MyTask extends DefaultTask {
                @Inject
                public abstract ObjectFactory getObjectFactory()

                @Input
                $elementInitialization

                @TaskAction
                void action() {
                    logger.warn("Input file: {}", $elementRead)
                }
            }

            tasks.register('myTask', MyTask)
        """

        expect:
        executer.expectDeprecationWarning("Type 'MyTask' property '$elementName' has @Input annotation used on property of type '$elementType'. " +
                "Reason: A property of type '$elementType' annotated with @Input cannot determine how to interpret the file.")
        fails 'myTask'
        result.assertHasErrorOutput("A problem was found with the configuration of task ':myTask' (type 'MyTask').")
        result.assertHasErrorOutput("- Type 'MyTask' property '$elementName' doesn't have a configured value.")
        result.assertHasErrorOutput("Reason: This property isn't marked as optional and no value has been configured.")
        result.assertHasErrorOutput("Possible solutions:")
        result.assertHasErrorOutput("1. Assign a value to 'myProp'.")
        result.assertHasErrorOutput("2. Mark property 'myProp' as optional.")
        result.assertTaskNotExecuted('myTask')
        result.assertHasErrorOutput(WorkValidationException.class.getTypeName())

        where:
        elementType             | elementName   | elementInitialization                                                             | elementRead
        'RegularFileProperty'   | 'myProp'      | 'abstract RegularFileProperty getMyProp()'                                        | 'myProp.getAsFile().get().absolutePath'
    }

    def "using @Input annotation on #elementType directory elements fails validation with helpful error message"() {
        given:
        buildFile << """
            import groovy.transform.CompileStatic

            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            abstract class MyTask extends DefaultTask {
                @Input
                $elementInitialization

                @TaskAction
                void action() {
                    logger.warn("Input file: {}", $elementRead)
                }
            }

            tasks.register('myTask', MyTask)
        """

        expect:
        executer.expectDeprecationWarning("Type 'MyTask' property '$elementName' has @Input annotation used on property of type '$elementType'. " +
                "Reason: A property of type '$elementType' annotated with @Input cannot determine how to interpret the file.")
        succeeds 'myTask'

        where:
        elementType         | elementName   | elementInitialization                                                 | elementRead
       'Directory'          | 'myField'     | "Directory myField = project.layout.projectDirectory.dir('myDir')"    | 'myField.getAsFile().absolutePath'
    }

    // This test should be removed in Gradle 8.0, as once the validation is restored to an error from the current warning, it will prevent the other problem from being detected
    def "using @Input annotation on #elementType directory elements and not setting a value fails validation with helpful error message"() {
        given:
        buildFile << """
            import groovy.transform.CompileStatic

            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            abstract class MyTask extends DefaultTask {
                @Input
                $elementInitialization

                @TaskAction
                void action() {
                    logger.warn("Input file: {}", $elementRead)
                }
            }

            tasks.register('myTask', MyTask)
        """

        expect:
        executer.expectDeprecationWarning("Type 'MyTask' property '$elementName' has @Input annotation used on property of type '$elementType'. " +
                "Reason: A property of type '$elementType' annotated with @Input cannot determine how to interpret the file.")
        fails 'myTask'
        result.assertHasErrorOutput("A problem was found with the configuration of task ':myTask' (type 'MyTask').")
        result.assertHasErrorOutput("- Type 'MyTask' property '$elementName' doesn't have a configured value.")
        result.assertHasErrorOutput("Reason: This property isn't marked as optional and no value has been configured.")
        result.assertHasErrorOutput("Possible solutions:")
        result.assertHasErrorOutput("1. Assign a value to 'myProp'.")
        result.assertHasErrorOutput("2. Mark property 'myProp' as optional.")
        result.assertTaskNotExecuted('myTask')
        result.assertHasErrorOutput(WorkValidationException.class.getTypeName())

        where:
        elementType         | elementName   | elementInitialization                                                 | elementRead
        "DirectoryProperty" | 'myProp'      | "abstract DirectoryProperty getMyProp()"                              | "myProp.getAsFile().get().absolutePath"
    }

    def "using @Input annotation on #propertyType file properties succeeds"() {
        given:
        buildFile << """
            import groovy.transform.CompileStatic

            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            abstract class MyTask extends DefaultTask {
                @Inject
                public abstract ObjectFactory getObjectFactory()

                @Input
                $propertyInitialization

                @TaskAction
                void action() {
                    logger.warn("Input file: {}", $propertyRead)
                }
            }

            tasks.register('myTask', MyTask) {
                $propertyAssignment
            }
        """

        expect:
        succeeds 'myTask'

        where:
        propertyType            | propertyInitialization                                                        | propertyAssignment                                                        | propertyRead
        "Property<File>"        | "final Property<File> myProp = getObjectFactory().property(File.class)"       | "myProp.set(project.layout.projectDirectory.file('myFile').getAsFile())"  | 'myProp.get().absolutePath'
    }
}
