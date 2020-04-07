/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.tasks.TasksWithInputsAndOutputs
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class FilePropertyLifecycleIntegrationTest extends AbstractIntegrationSpec implements TasksWithInputsAndOutputs {
    @Unroll
    def "task #annotation file property is implicitly finalized when task starts execution"() {
        buildFile << """
            class SomeTask extends DefaultTask {
                ${annotation}
                final RegularFileProperty prop = project.objects.fileProperty()

                @TaskAction
                void go() {
                    println "value: " + prop.get()
                }
            }

            task show(type: SomeTask) {
                prop = file("in.txt")
                doFirst {
                    prop = file("other.txt")
                }
            }
"""
        file("in.txt").createFile()

        when:
        fails("show")

        then:
        failure.assertHasDescription("Execution failed for task ':show'.")
        failure.assertHasCause("The value for task ':show' property 'prop' is final and cannot be changed any further.")

        where:
        annotation    | _
        "@InputFile"  | _
        "@OutputFile" | _
    }

    @Unroll
    def "task #annotation directory property is implicitly finalized when task starts execution"() {
        buildFile << """
            class SomeTask extends DefaultTask {
                ${annotation}
                final DirectoryProperty prop = project.objects.directoryProperty()

                @TaskAction
                void go() {
                    println "value: " + prop.get()
                }
            }

            task show(type: SomeTask) {
                prop = file("in.dir")
                doFirst {
                    prop = file("other.dir")
                }
            }
"""
        file("in.dir").createDir()

        when:
        fails("show")

        then:
        failure.assertHasDescription("Execution failed for task ':show'.")
        failure.assertHasCause("The value for task ':show' property 'prop' is final and cannot be changed any further.")

        where:
        annotation         | _
        "@InputDirectory"  | _
        "@OutputDirectory" | _
    }

    @Unroll
    def "task ad hoc file property registered using #registrationMethod is implicitly finalized when task starts execution"() {
        given:
        buildFile << """

def prop = project.objects.fileProperty()

task thing {
    ${registrationMethod}(prop)
    prop.set(file("file-1"))
    doLast {
        prop.set(file("ignored"))
        println "prop = " + prop.get()
    }
}
"""
        file("file-1").createFile()

        when:
        fails("thing")

        then:
        failure.assertHasDescription("Execution failed for task ':thing'.")
        failure.assertHasCause("The value for this property is final and cannot be changed any further.")

        where:
        registrationMethod | _
        "inputs.file"      | _
        "outputs.file"     | _
    }

    @Unroll
    def "task ad hoc directory property registered using #registrationMethod is implicitly finalized when task starts execution"() {
        given:
        buildFile << """

def prop = project.objects.directoryProperty()

task thing {
    ${registrationMethod}(prop)
    prop.set(file("file-1"))
    doLast {
        prop.set(file("ignored"))
        println "prop = " + prop.get()
    }
}
"""
        file("file-1").createDir()

        when:
        fails("thing")

        then:
        failure.assertHasDescription("Execution failed for task ':thing'.")
        failure.assertHasCause("The value for this property is final and cannot be changed any further.")

        where:
        registrationMethod | _
        "inputs.dir"       | _
        "outputs.dir"      | _
    }

    def "can query task output file property at any time"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            task producer(type: FileProducer) {
                output = layout.buildDir.file("text.out")
            }
            println("prop = " + producer.output.get())
            task after {
                dependsOn(producer)
                doLast {
                    println("prop = " + producer.output.get())
                }
            }
            task before {
                doLast {
                    println("prop = " + producer.output.get())
                }
            }
            producer.dependsOn(before)
        """

        expect:
        succeeds("after")
    }

    def "can query task output directory property at any time"() {
        taskTypeWithOutputDirectoryProperty()
        buildFile << """
            task producer(type: DirProducer) {
                output = layout.buildDir.dir("dir.out")
                names = ["a", "b"]
            }
            println("prop = " + producer.output.get())
            task after {
                dependsOn(producer)
                doLast {
                    println("prop = " + producer.output.get())
                }
            }
            task before {
                doLast {
                    println("prop = " + producer.output.get())
                }
            }
            producer.dependsOn(before)
        """

        expect:
        succeeds("after")
    }

    def "can query mapped task output file location property at any time"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            task producer(type: FileProducer) {
                output = layout.buildDir.file("text.out")
            }
            def prop = producer.output.locationOnly.map { it.asFile.name }
            println("prop = " + prop.get())
            task after {
                dependsOn(producer)
                doLast {
                    println("prop = " + prop.get())
                }
            }
            task before {
                doLast {
                    println("prop = " + prop.get())
                }
            }
            producer.dependsOn(before)
        """

        expect:
        succeeds("after")
    }

    def "can query mapped task output directory location property at any time"() {
        taskTypeWithOutputDirectoryProperty()
        buildFile << """
            task producer(type: DirProducer) {
                output = layout.buildDir.dir("dir.out")
                names = ["a", "b"]
            }
            def prop = producer.output.locationOnly.map { it.asFile.name }
            println("prop = " + prop.get())
            task after {
                dependsOn(producer)
                doLast {
                    println("prop = " + prop.get())
                }
            }
            task before {
                doLast {
                    println("prop = " + prop.get())
                }
            }
            producer.dependsOn(before)
        """

        expect:
        succeeds("after")
    }

    def "querying the value of a mapped task output file property before the task has started is deprecated"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            task producer(type: FileProducer) {
                output = layout.buildDir.file("text.out")
            }
            def prop = producer.output.map { it.asFile.file ? it.asFile.text : "(null)" }
            println("prop = " + prop.get())
        """

        when:
        executer.expectDocumentedDeprecationWarning("Querying the mapped value of task ':producer' property 'output' before task ':producer' has completed has been deprecated. " +
            "This will fail with an error in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#querying_a_mapped_output_property_of_a_task_before_the_task_has_completed")
        succeeds("producer")

        then:
        outputContains("prop = (null)")
    }

    def "querying the value of a mapped task output file property before the task has completed is deprecated"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            task producer(type: FileProducer) {
                output = layout.buildDir.file("text.out")
            }
            def prop = producer.output.map { it.asFile.file ? it.asFile.text : "(null)" }
            producer.doFirst {
                println("prop = " + prop.get())
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("Querying the mapped value of task ':producer' property 'output' before task ':producer' has completed has been deprecated. " +
            "This will fail with an error in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#querying_a_mapped_output_property_of_a_task_before_the_task_has_completed")
        succeeds("producer")

        then:
        outputContains("prop = (null)")
    }

    def "querying the value of a mapped task output directory property before the task has started is deprecated"() {
        taskTypeWithOutputDirectoryProperty()
        buildFile << """
            task producer(type: DirProducer) {
                output = layout.buildDir.dir("dir.out")
                names = ["a", "b"]
            }
            def prop = producer.output.map { it.asFile.directory ? it.asFile.list().length : -1 }
            println("prop = " + prop.get())
        """

        when:
        executer.expectDocumentedDeprecationWarning("Querying the mapped value of task ':producer' property 'output' before task ':producer' has completed has been deprecated. " +
            "This will fail with an error in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#querying_a_mapped_output_property_of_a_task_before_the_task_has_completed")
        succeeds("producer")

        then:
        outputContains("prop = -1")
    }

    def "querying the value of a mapped task output directory property before the task has completed is deprecated"() {
        taskTypeWithOutputDirectoryProperty()
        buildFile << """
            task producer(type: DirProducer) {
                output = layout.buildDir.dir("dir.out")
                names = ["a", "b"]
            }
            def prop = producer.output.map { it.asFile.directory ? it.asFile.list().length : -1 }
            producer.doFirst {
                println("prop = " + prop.get())
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("Querying the mapped value of task ':producer' property 'output' before task ':producer' has completed has been deprecated. " +
            "This will fail with an error in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#querying_a_mapped_output_property_of_a_task_before_the_task_has_completed")
        succeeds("producer")

        then:
        outputContains("prop = 0")
    }
}
