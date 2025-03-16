/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.cc.impl

import javax.inject.Inject
import java.util.logging.Level

class ConfigurationCacheTaskSerializationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "using a tasks from same project as 'files(#type)' input is allowed"() {
        file("copy1source.txt") << "Copy 1"
        file("copy2source.txt") << "Copy 2"

        buildFile << """
            def copy1 = tasks.register("copy1", Copy) {
                destinationDir = layout.buildDirectory.dir("copy1").get().asFile
                from file("copy1source.txt")
            }

            def copy2 = tasks.register("copy2", Copy) {
                destinationDir = layout.buildDirectory.dir("copy2").get().asFile
                from file("copy2source.txt")
            }

            tasks.register("reader") {
                inputs.files($tasksInput)
                doLast {
                    println inputs.files.files*.name
                }
            }
        """

        when:
        configurationCacheRun "reader"

        then:
        outputContains expectedOutput

        where:
        type                             | tasksInput             | expectedOutput
        "Task"                           | "copy1.get()"          | "[copy1]"
        "TaskProvider"                   | "copy1"                | "[copy1]"
        "Array[Task, TaskProvider]"      | "copy1.get(), copy2"   | "[copy1, copy2]"
        "Collection(Task, TaskProvider)" | "[copy1.get(), copy2]" | "[copy1, copy2]"
    }

    def "using a tasks from another project as 'files(#type)' input is prohibited"() {
        settingsFile << """
            include ':foo'
        """

        buildFile << """
            tasks.register("dependency")
        """

        file("foo/build.gradle") << """
            tasks.register("dependent") {
                inputs.files(parent.tasks.findByPath(':dependency'))
            }
        """

        when:
        configurationCacheFails ":foo:dependent"

        then:
        problems.assertFailureHasProblems(failure) {
            withProblem("Task `:foo:dependent` of type `org.gradle.api.DefaultTask`: cannot serialize object of type 'org.gradle.api.DefaultTask'")
            totalProblemsCount = 1
            problemsWithStackTraceCount = 0
        }
    }

    def "restores task fields whose value is an object graph with cycles"() {
        buildFile << """
            class SomeBean {
                String value
                SomeBean parent
                SomeBean child

                SomeBean(String value) {
                    println("creating bean")
                    this.value = value
                }
            }

            class SomeTask extends DefaultTask {
                @Internal
                final SomeBean bean

                SomeTask() {
                    bean = new SomeBean("default")
                    bean.parent = new SomeBean("parent")
                    bean.parent.child = bean
                    bean.parent.parent = bean.parent
                }

                @TaskAction
                void run() {
                    println "bean.value = " + bean.value
                    println "bean.parent.value = " + bean.parent.value
                    println "same reference = " + (bean.parent.child == bean)
                }
            }

            task ok(type: SomeTask) {
                bean.value = "child"
            }
        """

        when:
        configurationCacheRun "ok"

        then:
        result.output.count("creating bean") == 2

        when:
        configurationCacheRun "ok"

        then:
        outputDoesNotContain("creating bean")
        outputContains("bean.value = child")
        outputContains("bean.parent.value = parent")
        outputContains("same reference = true")
    }

    def "replaces provider with fixed value"() {
        buildFile << """
            class SomeTask extends DefaultTask {
                @Internal
                Provider<String> value

                @TaskAction
                void run() {
                    println "this.value = " + value.getOrNull()
                }
            }

            task ok(type: SomeTask) {
                value = providers.provider {
                    println("calculating value")
                    'value'
                }
            }
        """

        when:
        configurationCacheRun "ok"

        then:
        outputContains("calculating value")
        outputContains("this.value = value")

        when:
        configurationCacheRun "ok"

        then:
        outputDoesNotContain("calculating value")
        outputContains("this.value = value")
    }

    def "Directory value can resolve paths after being restored"() {
        buildFile << """
            import ${Inject.name}

            class SomeTask extends DefaultTask {
                @Internal
                Directory value
                @Internal
                final Property<Directory> propValue

                @Inject
                SomeTask(ObjectFactory objects) {
                    propValue = objects.directoryProperty()
                }

                @TaskAction
                void run() {
                    println "value = " + value
                    println "value.child = " + value.dir("child")
                    println "propValue = " + propValue.get()
                    println "propValue.child = " + propValue.get().dir("child")
                    println "propValue.child.mapped = " + propValue.dir("child").get()
                }
            }

            task ok(type: SomeTask) {
                value = layout.projectDir.dir("dir1")
                propValue = layout.projectDir.dir("dir2")
            }
        """

        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

        then:
        outputContains("value = ${file("dir1")}")
        outputContains("value.child = ${file("dir1/child")}")
        outputContains("propValue = ${file("dir2")}")
        outputContains("propValue.child = ${file("dir2/child")}")
        outputContains("propValue.child.mapped = ${file("dir2/child")}")
    }

    def "restores task abstract properties"() {
        buildFile << """
            interface Bean {
                @Internal
                Property<String> getValue()

                @Internal
                Property<String> getUnused()
            }

            abstract class SomeTask extends DefaultTask {
                @Nested
                abstract Bean getBean()

                @Nested
                abstract Bean getUnusedBean()

                @Internal
                abstract Property<String> getValue()

                @Internal
                abstract Property<String> getUnused()

                @TaskAction
                void run() {
                    println "this.value = " + value.getOrNull()
                    println "this.unused = " + unused.getOrNull()
                    println "this.bean.value = " + bean.value.getOrNull()
                    println "this.bean.unused = " + bean.unused.getOrNull()
                    println "this.unusedBean.value = " + unusedBean.value.getOrNull()
                }
            }

            task ok(type: SomeTask) {
                value = "42"
                bean.value = "42"
            }
        """

        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

        then:
        outputContains("this.value = 42")
        outputContains("this.unused = null")
        outputContains("this.bean.value = 42")
        outputContains("this.bean.unused = null")
        outputContains("this.unusedBean.value = null")
    }

    def "restores nested task abstract properties of type #type"() {
        given:
        buildFile """
            abstract class SomeTask extends DefaultTask {

                abstract static class SomeTaskInputs {

                    @Internal
                    abstract $type getProperty()

                    void run() {
                        println('task.nested.property = ' + property.orNull)
                    }
                }

                @Nested
                abstract SomeTaskInputs getSomeTaskInputs()

                @TaskAction
                void run() {
                    someTaskInputs.run()
                }
            }

            tasks.register('ok', SomeTask) {
                someTaskInputs.property = $reference
            }
        """
        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

        then:
        outputContains("task.nested.property = $output")

        where:
        type                    | reference            | output
        "Property<String>"      | "'value'"            | "value"
        "Property<String>"      | "null"               | "null"
        "Property<$Level.name>" | "${Level.name}.INFO" | "INFO"
    }

    def "task can reference itself"() {
        buildFile << """
            class SomeBean {
                private SomeTask owner
            }

            class SomeTask extends DefaultTask {
                private final SomeTask thisTask
                private final bean = new SomeBean()

                SomeTask() {
                    thisTask = this
                    bean.owner = this
                }

                @TaskAction
                void run() {
                    println "thisTask = " + (thisTask == this)
                    println "bean.owner = " + (bean.owner == this)
                }
            }

            task ok(type: SomeTask)
        """

        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

        then:
        outputContains("thisTask = true")
        outputContains("bean.owner = true")
    }

    def "retains Property identity for each task"() {
        buildFile << """
            abstract class SomeTask extends DefaultTask {
                @Internal
                abstract Property<String> getValue()

                @TaskAction
                void run() {
                    println "this.value = " + value.getOrNull()
                }
            }

            task ok(type: SomeTask) {
                value = "42"
                def valueRef = value
                doFirst {
                    valueRef.set("123")
                }
            }

            task other {
                mustRunAfter(tasks.ok)
                def value = tasks.ok.value
                doLast {
                    println("ok.value = " + value.getOrNull())
                }
            }
        """

        when:
        configurationCacheRun "ok", "other"

        then:
        outputContains("this.value = 123")
        outputContains("ok.value = 42")

        when:
        configurationCacheRun "ok", "other"

        then:
        outputContains("this.value = 123")
        outputContains("ok.value = 42")
    }

    def "retains ConfigurableFileCollection identity for each task"() {
        buildFile << """
            abstract class SomeTask extends DefaultTask {
                @Internal
                abstract ConfigurableFileCollection getValue()

                @TaskAction
                void run() {
                    println "this.value = " + value*.name
                }
            }

            task ok(type: SomeTask) {
                value.from("file1.txt")
                def valueRef = value
                doFirst {
                    valueRef.from("file2.txt")
                }
            }

            task other {
                mustRunAfter(tasks.ok)
                def value = tasks.ok.value
                doLast {
                    println("ok.value = " + value*.name)
                }
            }
        """

        when:
        configurationCacheRun "ok", "other"

        then:
        outputContains("this.value = [file1.txt, file2.txt]")
        outputContains("ok.value = [file1.txt]")

        when:
        configurationCacheRun "ok", "other"

        then:
        outputContains("this.value = [file1.txt, file2.txt]")
        outputContains("ok.value = [file1.txt]")
    }
}
