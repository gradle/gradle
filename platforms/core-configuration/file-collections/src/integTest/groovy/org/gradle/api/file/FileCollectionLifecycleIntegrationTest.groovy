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

import groovy.test.NotYetImplemented
import org.gradle.api.tasks.TasksWithInputsAndOutputs
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class FileCollectionLifecycleIntegrationTest extends AbstractIntegrationSpec implements TasksWithInputsAndOutputs {
    def "UPGRADED task #annotation configurable file collection is LENIENTLY implicitly finalized when task starts execution UNTIL NEXT MAJOR"() {
        executer.requireOwnGradleUserHomeDir("temp")
        buildFile << """
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty

            abstract class SomeTask extends DefaultTask {
                @ReplacesEagerProperty
                ${annotation}
                abstract ConfigurableFileCollection getProp()

                @TaskAction
                void go() {
                    println "value: " + prop.files
                }
            }

            task show(type: SomeTask) {
                def layout = project.layout
                prop.setFrom([layout.projectDir.file("in.txt")])
                doFirst {
                    prop.setFrom([layout.projectDir.file("other.txt")])
                }
            }
        """

        expect:
        executer.expectDeprecationWarningWithPattern("Changing property value of task ':show' property 'prop' at execution time. This behavior has been deprecated.*")
        succeeds("show")
        outputContains("value: [${file('other.txt')}]")

        where:
        annotation     | _
        "@InputFiles"  | _
        "@OutputFiles" | _
    }

    def "task #annotation configurable file collection is implicitly finalized when task starts execution"() {
        executer.requireOwnGradleUserHomeDir("temp")
        buildFile << """
            abstract class SomeTask extends DefaultTask {
                $propertyInit

                ${annotation}
                $getter

                @TaskAction
                void go() {
                    println "value: " + prop.files
                }
            }

            task show(type: SomeTask) {
                def layout = project.layout
                prop.setFrom([layout.projectDir.file("in.txt")])
                doFirst {
                    prop.setFrom([layout.projectDir.file("other.txt")])
                }
            }
        """

        when:
        fails("show")

        then:
        failure.assertHasCause("The value for task ':show' property 'prop' is final and cannot be changed any further.")

        where:
        annotation     | getter                                                      | propertyInit
        "@InputFiles"  | "abstract ConfigurableFileCollection getProp()"             | ""
        "@OutputFiles" | "abstract ConfigurableFileCollection getProp()"             | ""
        "@InputFiles"  | "ConfigurableFileCollection getProp() { return this.prop }" | "private final ConfigurableFileCollection prop = project.objects.fileCollection()"
        "@OutputFiles" | "ConfigurableFileCollection getProp() { return this.prop }" | "private final ConfigurableFileCollection prop = project.objects.fileCollection()"
    }

    def "finalized file collection resolves locations and ignores later changes to source paths"() {
        buildFile """
            def files = objects.fileCollection()
            Integer counter = 0
            files.from { "a\${++counter}" }

            def names = ['b', 'c']
            files.from(names)

            assert files.files as List == [file('a1'), file('b'), file('c')]

            files.finalizeValue()

            assert counter == 2

            assert files.files as List == [file('a2'), file('b'), file('c')]

            counter = 45
            names.clear()

            assert files.files as List == [file('a2'), file('b'), file('c')]
        """

        expect:
        succeeds()
    }

    def "finalize on read file collection resolves locations and ignores later changes to source paths"() {
        buildFile """
            def files = objects.fileCollection()
            Integer counter = 0
            files.from { "a\${++counter}" }

            def names = ['b', 'c']
            files.from(names)

            assert files.files as List == [file('a1'), file('b'), file('c')]

            files.finalizeValueOnRead()

            assert counter == 1

            assert files.files as List == [file('a2'), file('b'), file('c')]

            counter = 45
            names.clear()

            assert files.files as List == [file('a2'), file('b'), file('c')]
        """

        expect:
        succeeds()
    }

    def "finalized file collection ignores later changes to nested file collections"() {
        buildFile """
            def nested = objects.fileCollection()
            def name = 'a'
            nested.from { name }

            def names = ['b', 'c']
            nested.from(names)

            def files = objects.fileCollection()
            files.from(nested)
            files.finalizeValue()
            name = 'ignore-me'
            names.clear()
            nested.from('ignore-me')

            assert files.files as List == [file('a'), file('b'), file('c')]
        """

        expect:
        succeeds()
    }

    def "finalized file collection still reflects changes to file system but not changes to locations"() {
        buildFile """
            def files = objects.fileCollection()
            def name = 'a'
            files.from {
                fileTree({ name }) {
                    include '**/*.txt'
                }
            }

            files.finalizeValue()
            name = 'b'

            assert files.files.empty

            file('a').mkdirs()
            def f1 = file('a/thing.txt')
            f1.text = 'thing'

            assert files.files as List == [f1]
        """

        expect:
        succeeds()
    }

    def "cannot mutate finalized file collection"() {
        buildFile """
            def files = objects.fileCollection()
            files.finalizeValue()

            task broken {
                doLast {
                    files.from('bad')
                }
            }
        """

        when:
        fails('broken')

        then:
        failure.assertHasCause("The value for this file collection is final and cannot be changed any further.")
    }

    def "can disallow changes to file collection without finalizing value"() {
        buildFile """
            def files = objects.fileCollection()
            def name = 'other'
            files.from { name }

            def names = ['b', 'c']
            files.from(names)

            files.disallowChanges()
            name = 'a'
            names.clear()

            assert files.files as List == [file('a')]

            files.from('broken')
        """

        when:
        fails('broken')

        then:
        failure.assertHasCause("The value for this file collection cannot be changed any further.")
    }

    def "can write but cannot read strict project file collection instance before project configuration completes"() {
        given:
        settingsFile << 'rootProject.name = "broken"'
        buildFile """
            interface ProjectModel {
                ConfigurableFileCollection getProp()
            }

            project.extensions.create('thing', ProjectModel.class)
            thing.prop.disallowUnsafeRead()
            thing.prop.from(layout.buildDirectory)

            try {
                thing.prop.files
            } catch(IllegalStateException e) {
                println("get files failed with: " + e.message)
            }

            def elements = thing.prop.elements
            try {
                elements.get()
            } catch(IllegalStateException e) {
                println("get elements failed with: " + e.message)
            }

            thing.prop.from = "some-file-1"

            afterEvaluate {
                thing.prop.from = ["some-file-2"]
                try {
                    thing.prop.files
                } catch(IllegalStateException e) {
                    println("get files in afterEvaluate failed with: " + e.message)
                }
                try {
                    thing.prop.elements.get()
                } catch(IllegalStateException e) {
                    println("get elements in afterEvaluate failed with: " + e.message)
                }
            }

            task show {
                def thing = project.thing
                // Task graph calculation is ok
                dependsOn {
                    println("value = " + thing.prop.files)
                    println("elements = " + thing.prop.elements.get())
                    try {
                        thing.prop.from = 'ignore me'
                    } catch(IllegalStateException e) {
                        println("set after read failed with: " + e.message)
                    }
                }
                doLast {
                    println("value = " + thing.prop.files)
                    println("elements = " + thing.prop.elements.get())
                }
            }
        """


        when:
        run("show")

        then:
        outputContains("get files failed with: Cannot query the value of extension 'thing' property 'prop' because configuration of root project 'broken' has not completed yet.")
        outputContains("get elements failed with: Cannot query the value of extension 'thing' property 'prop' because configuration of root project 'broken' has not completed yet.")
        outputContains("get files in afterEvaluate failed with: Cannot query the value of extension 'thing' property 'prop' because configuration of root project 'broken' has not completed yet.")
        outputContains("get elements in afterEvaluate failed with: Cannot query the value of extension 'thing' property 'prop' because configuration of root project 'broken' has not completed yet.")
        outputContains("set after read failed with: The value for extension 'thing' property 'prop' is final and cannot be changed any further.")
        output.count("value = [${file('some-file-2')}]") == 2
        output.count("elements = [${file('some-file-2')}]") == 2
    }

    def "can change value of strict file collection after project configuration completes and before the value has been read"() {
        given:
        settingsFile << 'rootProject.name = "broken"'
        buildFile """
            interface ProjectModel {
                ConfigurableFileCollection getProp()
            }

            project.extensions.create('thing', ProjectModel.class)
            thing.prop.disallowUnsafeRead()

            task show {
                def thing = project.thing
                dependsOn {
                    thing.prop.from("some-file")
                    println("value = " + thing.prop.files)
                    try {
                        thing.prop.from("ignore me")
                    } catch(IllegalStateException e) {
                        println("set failed with: " + e.message)
                    }
                }
                doLast {
                    println("value = " + thing.prop.files)
                }
            }
        """

        when:
        run("show")

        then:
        outputContains("set failed with: The value for extension 'thing' property 'prop' is final and cannot be changed any further.")
        output.count("value = [${file('some-file')}]") == 2
    }

    def "cannot finalize a strict file collection before project configuration completes"() {
        given:
        settingsFile << 'rootProject.name = "broken"'
        buildFile """
            interface ProjectModel {
                ConfigurableFileCollection getProp()
            }

            project.extensions.create('thing', ProjectModel.class)
            thing.prop.disallowUnsafeRead()

            try {
                thing.prop.finalizeValue()
            } catch(IllegalStateException e) {
                println("finalize failed with: " + e.message)
            }

            thing.prop.from("some-file")

            task show {
                def thing = project.thing
                dependsOn {
                    thing.prop.finalizeValue()
                    println("value = " + thing.prop.files)
                }
                doLast {
                    println("value = " + thing.prop.files)
                }
            }
        """

        when:
        run("show")

        then:
        outputContains("finalize failed with: Cannot finalize the value of extension 'thing' property 'prop' because configuration of root project 'broken' has not completed yet.")
        output.count("value = [${file('some-file')}]") == 2
    }

    @NotYetImplemented
    def "finalizing value on read with a ConfigurableFileCollection does not lose dependency information"() {
        buildFile """
            abstract class Generate extends DefaultTask {
                @OutputDirectory
                abstract DirectoryProperty getOutputDirectory()

                @TaskAction
                void generate() {
                    def outputFile = outputDirectory.get().file("generated.txt").asFile
                    outputFile.text = "generated file"
                }
            }

            def generateFile = tasks.register('generate', Generate) {
                outputDirectory.convention(layout.buildDirectory)
            }

            interface ProjectModel {
                ConfigurableFileCollection getGeneratedFiles()
            }

            def thing = project.extensions.create("thing", ProjectModel)
            thing.generatedFiles.from(generateFile)
            thing.generatedFiles.finalizeValueOnRead()

            task show {
                dependsOn thing.generatedFiles
            }
        """
        when:
        run("show")
        then:
        result.assertTasksExecuted(":generate", ":show")
    }
}
