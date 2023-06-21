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
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.reflect.validation.ValidationTestFor
import spock.lang.Issue

class TestInputAnnotationFailuresIntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker {
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
        result.assertHasErrorOutput("A problem was found with the configuration of task ':myTask' (type 'MyTask').")
        result.assertHasErrorOutput("- Type 'MyTask' property '$elementName' has @Input annotation used on property of type '$elementType'.")
        result.assertHasErrorOutput("Reason: A property of type '$elementType' annotated with @Input cannot determine how to interpret the file.")
        result.assertHasErrorOutput("Possible solutions:")
        result.assertHasErrorOutput("1. Annotate with @InputFile for regular files.")
        result.assertHasErrorOutput("2. Annotate with @InputFiles for collections of files.")
        result.assertHasErrorOutput(". If you want to track the path, return File.absolutePath as a String and keep @Input.")
        result.assertTaskNotExecuted('myTask')

        where:
        elementType             | elementName   | elementInitialization                                                             | elementRead
        'File'                  | 'myField'     | "File myField = project.layout.projectDirectory.file('myFile.txt').getAsFile()"   | 'myField.absolutePath'
        'RegularFile'           | 'myField'     | "RegularFile myField = project.layout.projectDirectory.file('myFile.txt')"        | 'myField.getAsFile().absolutePath'
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
        fails 'myTask'
        result.assertHasErrorOutput("A problem was found with the configuration of task ':myTask' (type 'MyTask').")
        result.assertHasErrorOutput("- Type 'MyTask' property '$elementName' has @Input annotation used on property of type '$elementType'.")
        result.assertHasErrorOutput("Reason: A property of type '$elementType' annotated with @Input cannot determine how to interpret the file.")
        result.assertHasErrorOutput("Possible solution: Annotate with @InputDirectory for directories.")
        result.assertTaskNotExecuted('myTask')

        where:
        elementType         | elementName   | elementInitialization                                                 | elementRead
       'Directory'          | 'myField'     | "Directory myField = project.layout.projectDirectory.dir('myDir')"    | 'myField.getAsFile().absolutePath'
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

    @Issue("https://github.com/gradle/gradle/issues/24979")
    @ValidationTestFor(ValidationProblemId.UNSUPPORTED_VALUE_TYPE)
    def "cannot annotate type 'java.net.URL' with @Input"() {

        executer.beforeExecute {
            executer.noDeprecationChecks()
            executer.withArgument("-Dorg.gradle.internal.max.validation.errors=20")
        }

        given:
        buildFile << """
            interface NestedBean {
                @Input
                Property<URL> getNested()
            }

            abstract class TaskWithInput extends DefaultTask {

                private final NestedBean nested = project.objects.newInstance(NestedBean.class)

                @Input
                URL getDirect() { null }

                @Input
                Provider<URL> getProviderInput() { propertyInput }

                @Input
                abstract Property<URL> getPropertyInput();

                @Input
                abstract SetProperty<URL> getSetPropertyInput();

                @Input
                abstract ListProperty<URL> getListPropertyInput();

                @Input
                abstract MapProperty<String, URL> getMapPropertyInput();

                @Nested
                abstract NestedBean getNestedInput();
            }

            tasks.register('verify', TaskWithInput) {
                def url = uri("https://gradle.org").toURL()
                propertyInput.set(url)
                setPropertyInput.set([url])
                listPropertyInput.set([url])
                mapPropertyInput.put("some", url)
                nestedInput.nested.set(url)
                doLast {
                    println(setPropertyInput.get())
                }
            }
        """

        when:
        fails "verify"

        then:
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer,
            "Type 'TaskWithInput' property 'direct' has @Input annotation used on type 'java.net.URL' or a property of this type. " +
                "Type 'java.net.URL' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type",
            'validation_problems', 'unsupported_value_type')
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer,
            "Type 'TaskWithInput' property 'providerInput' has @Input annotation used on type 'java.net.URL' or a property of this type. " +
                "Type 'java.net.URL' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type",
            'validation_problems', 'unsupported_value_type')
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer,
            "Type 'TaskWithInput' property 'propertyInput' has @Input annotation used on type 'java.net.URL' or a property of this type. " +
                "Type 'java.net.URL' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type",
            'validation_problems', 'unsupported_value_type')
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer,
            "Type 'TaskWithInput' property 'setPropertyInput' has @Input annotation used on type 'java.net.URL' or a property of this type. " +
                "Type 'java.net.URL' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type",
            'validation_problems', 'unsupported_value_type')
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer,
            "Type 'TaskWithInput' property 'listPropertyInput' has @Input annotation used on type 'java.net.URL' or a property of this type. " +
                "Type 'java.net.URL' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type",
            'validation_problems', 'unsupported_value_type')
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer,
            "Type 'TaskWithInput' property 'mapPropertyInput' has @Input annotation used on type 'java.net.URL' or a property of this type. " +
                "Type 'java.net.URL' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type",
            'validation_problems', 'unsupported_value_type')
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer,
            "Type 'TaskWithInput' property 'nestedInput.nested' has @Input annotation used on type 'java.net.URL' or a property of this type. " +
                "Type 'java.net.URL' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type",
            'validation_problems', 'unsupported_value_type')
    }
}
