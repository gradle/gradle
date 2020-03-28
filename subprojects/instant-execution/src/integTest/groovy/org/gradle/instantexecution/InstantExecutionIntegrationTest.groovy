/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.model.ObjectFactory
import org.gradle.initialization.LoadProjectsBuildOperationType
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.event.ListenerManager
import org.gradle.jvm.toolchain.JavaInstallationRegistry
import org.gradle.process.ExecOperations
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.workers.WorkerExecutor
import org.slf4j.Logger
import spock.lang.Unroll

import javax.inject.Inject


class InstantExecutionIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "instant execution for help on empty project"() {
        given:
        instantRun "help"
        def firstRunOutput = result.normalizedOutput
            .replaceAll(/Calculating task graph as no instant execution cache is available for tasks: help\n/, '')
            .replaceAll(/Watching \d+ (directory hierarchies to track changes between builds in \d+ directories|directories to track changes between builds)\n/, '')
            .replaceAll(/Spent \d+ ms registering watches for file system events\n/, '')
            .replaceAll(/Virtual file system .*\n/, '')

        when:
        instantRun "help"
        def secondRunOutput = result.normalizedOutput
            .replaceAll(/Reusing instant execution cache. This is not guaranteed to work in any way.\n/, '')
            .replaceAll(/Received \d+ file system events .*\n/, '')
            .replaceAll(/Spent \d+ ms processing file system events since last build\n/, '')
            .replaceAll(/Watching \d+ (directory hierarchies to track changes between builds in \d+ directories|directories to track changes between builds)\n/, '')
            .replaceAll(/Spent \d+ ms registering watches for file system events\n/, '')
            .replaceAll(/Virtual file system .*\n/, '')

        then:
        firstRunOutput == secondRunOutput
    }

    def "restores some details of the project structure"() {
        def fixture = new BuildOperationsFixture(executer, temporaryFolder)

        settingsFile << """
            rootProject.name = 'thing'
            include 'a', 'b', 'c'
            include 'a:b'
            project(':a:b').projectDir = file('custom')
            gradle.rootProject {
                allprojects {
                    task thing
                }
            }
        """

        when:
        instantRun "help"

        then:
        def event = fixture.first(LoadProjectsBuildOperationType)
        event.result.rootProject.name == 'thing'
        event.result.rootProject.path == ':'
        event.result.rootProject.children.size() == 3 // All projects are created when storing

        when:
        instantRun "help"

        then:
        def event2 = fixture.first(LoadProjectsBuildOperationType)
        event2.result.rootProject.name == 'thing'
        event2.result.rootProject.path == ':'
        event2.result.rootProject.projectDir == testDirectory.absolutePath
        event2.result.rootProject.children.empty // None of the child projects are created when loading, as they have no tasks scheduled

        when:
        instantRun ":a:thing"

        then:
        def event3 = fixture.first(LoadProjectsBuildOperationType)
        event3.result.rootProject.name == 'thing'
        event3.result.rootProject.children.size() == 3 // All projects are created when storing

        when:
        instantRun ":a:thing"

        then:
        def event4 = fixture.first(LoadProjectsBuildOperationType)
        event4.result.rootProject.name == 'thing'
        event4.result.rootProject.path == ':'
        event4.result.rootProject.projectDir == testDirectory.absolutePath
        event4.result.rootProject.children.size() == 1 // Only project a is created when loading
        def project1 = event4.result.rootProject.children.first()
        project1.name == 'a'
        project1.path == ':a'
        project1.projectDir == file('a').absolutePath
        project1.children.empty

        when:
        instantRun ":a:b:thing"

        then:
        def event5 = fixture.first(LoadProjectsBuildOperationType)
        event5.result.rootProject.name == 'thing'
        event5.result.rootProject.children.size() == 3 // All projects are created when storing

        when:
        instantRun ":a:b:thing"

        then:
        def event6 = fixture.first(LoadProjectsBuildOperationType)
        event6.result.rootProject.name == 'thing'
        event6.result.rootProject.path == ':'
        event6.result.rootProject.projectDir == testDirectory.absolutePath
        event6.result.rootProject.children.size() == 1
        def project3 = event6.result.rootProject.children.first()
        project3.name == 'a'
        project3.path == ':a'
        project3.projectDir == file('a').absolutePath
        project3.children.size() == 1
        def project4 = project3.children.first()
        project4.name == 'b'
        project4.path == ':a:b'
        project4.projectDir == file('custom').absolutePath
    }

    def "does not configure build when task graph is already cached for requested tasks"() {

        def instantExecution = newInstantExecutionFixture()

        given:
        buildFile << """
            println "running build script"

            class SomeTask extends DefaultTask {
                SomeTask() {
                    println("create task")
                }
            }
            task a(type: SomeTask) {
                println("configure task")
            }
            task b {
                dependsOn a
            }
        """

        when:
        instantRun "a"

        then:
        instantExecution.assertStateStored()
        outputContains("Calculating task graph as no instant execution cache is available for tasks: a")
        outputContains("running build script")
        outputContains("create task")
        outputContains("configure task")
        result.assertTasksExecuted(":a")

        when:
        instantRun "a"

        then:
        instantExecution.assertStateLoaded()
        outputContains("Reusing instant execution cache. This is not guaranteed to work in any way.")
        outputDoesNotContain("running build script")
        outputDoesNotContain("create task")
        outputDoesNotContain("configure task")
        result.assertTasksExecuted(":a")

        when:
        instantRun "b"

        then:
        instantExecution.assertStateStored()
        outputContains("Calculating task graph as no instant execution cache is available for tasks: b")
        outputContains("running build script")
        outputContains("create task")
        outputContains("configure task")
        result.assertTasksExecuted(":a", ":b")

        when:
        instantRun "a"

        then:
        instantExecution.assertStateLoaded()
        outputContains("Reusing instant execution cache. This is not guaranteed to work in any way.")
        outputDoesNotContain("running build script")
        outputDoesNotContain("create task")
        outputDoesNotContain("configure task")
        result.assertTasksExecuted(":a")
    }

    def "instant execution for multi-level projects"() {
        given:
        settingsFile << """
            include 'a:b', 'a:c'
        """
        instantRun ":a:b:help", ":a:c:help"
        def firstRunOutput = result.groupedOutput

        when:
        instantRun ":a:b:help", ":a:c:help"

        then:
        result.groupedOutput.task(":a:b:help").output == firstRunOutput.task(":a:b:help").output
        result.groupedOutput.task(":a:c:help").output == firstRunOutput.task(":a:c:help").output
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
        instantRun "ok"

        then:
        result.output.count("creating bean") == 2

        when:
        instantRun "ok"

        then:
        outputDoesNotContain("creating bean")
        outputContains("bean.value = child")
        outputContains("bean.parent.value = parent")
        outputContains("same reference = true")
    }

    @Unroll
    def "restores task fields whose value is instance of #type"() {
        buildFile << """
            import java.util.concurrent.*

            class SomeBean {
                ${type} value
            }

            enum SomeEnum {
                One, Two
            }

            class SomeTask extends DefaultTask {
                private final SomeBean bean = new SomeBean()
                private final ${type} value

                SomeTask() {
                    value = ${reference}
                    bean.value = ${reference}
                }

                @TaskAction
                void run() {
                    println "this.value = " + value
                    println "bean.value = " + bean.value
                }
            }

            task ok(type: SomeTask)
        """

        when:
        instantRun "ok"
        instantRun "ok"

        then:
        outputContains("this.value = ${output}")
        outputContains("bean.value = ${output}")

        where:
        type                                 | reference                                                     | output
        String.name                          | "'value'"                                                     | "value"
        String.name                          | "null"                                                        | "null"
        Boolean.name                         | "true"                                                        | "true"
        boolean.name                         | "true"                                                        | "true"
        Character.name                       | "'a'"                                                         | "a"
        char.name                            | "'a'"                                                         | "a"
        Byte.name                            | "12"                                                          | "12"
        byte.name                            | "12"                                                          | "12"
        Short.name                           | "12"                                                          | "12"
        short.name                           | "12"                                                          | "12"
        Integer.name                         | "12"                                                          | "12"
        int.name                             | "12"                                                          | "12"
        Long.name                            | "12"                                                          | "12"
        long.name                            | "12"                                                          | "12"
        Float.name                           | "12.1"                                                        | "12.1"
        float.name                           | "12.1"                                                        | "12.1"
        Double.name                          | "12.1"                                                        | "12.1"
        double.name                          | "12.1"                                                        | "12.1"
        Class.name                           | "SomeBean"                                                    | "class SomeBean"
        "SomeEnum"                           | "SomeEnum.Two"                                                | "Two"
        "SomeEnum[]"                         | "[SomeEnum.Two] as SomeEnum[]"                                | "[Two]"
        "List<String>"                       | "['a', 'b', 'c']"                                             | "[a, b, c]"
        "ArrayList<String>"                  | "['a', 'b', 'c'] as ArrayList"                                | "[a, b, c]"
        "LinkedList<String>"                 | "['a', 'b', 'c'] as LinkedList"                               | "[a, b, c]"
        "Set<String>"                        | "['a', 'b', 'c'] as Set"                                      | "[a, b, c]"
        "HashSet<String>"                    | "['a', 'b', 'c'] as HashSet"                                  | "[a, b, c]"
        "LinkedHashSet<String>"              | "['a', 'b', 'c'] as LinkedHashSet"                            | "[a, b, c]"
        "TreeSet<String>"                    | "['a', 'b', 'c'] as TreeSet"                                  | "[a, b, c]"
        "EnumSet<SomeEnum>"                  | "EnumSet.of(SomeEnum.Two)"                                    | "[Two]"
        "Map<String, Integer>"               | "[a: 1, b: 2]"                                                | "[a:1, b:2]"
        "HashMap<String, Integer>"           | "new HashMap([a: 1, b: 2])"                                   | "[a:1, b:2]"
        "LinkedHashMap<String, Integer>"     | "new LinkedHashMap([a: 1, b: 2])"                             | "[a:1, b:2]"
        "TreeMap<String, Integer>"           | "new TreeMap([a: 1, b: 2])"                                   | "[a:1, b:2]"
        "ConcurrentHashMap<String, Integer>" | "new ConcurrentHashMap([a: 1, b: 2])"                         | "[a:1, b:2]"
        "EnumMap<SomeEnum, String>"          | "new EnumMap([(SomeEnum.One): 'one', (SomeEnum.Two): 'two'])" | "[One:one, Two:two]"
    }

    @Unroll
    def "restores task fields whose value is instance of plugin specific version of Guava #type"() {
        buildFile << """
            import ${type.name}

            buildscript {
                ${jcenterRepository()}
                dependencies {
                    classpath 'com.google.guava:guava:28.0-jre'
                }
            }

            class SomeBean {
                ${type.simpleName} value
            }

            class SomeTask extends DefaultTask {
                private final SomeBean bean = new SomeBean()
                private final ${type.simpleName} value

                SomeTask() {
                    value = ${reference}
                    bean.value = ${reference}
                }

                @TaskAction
                void run() {
                    println "this.value = " + value
                    println "bean.value = " + bean.value
                }
            }

            task ok(type: SomeTask)
        """

        when:
        instantRun "ok"
        instantRun "ok"

        then:
        outputContains("this.value = ${output}")
        outputContains("bean.value = ${output}")

        where:
        type          | reference                         | output
        ImmutableList | "ImmutableList.of('a', 'b', 'c')" | "[a, b, c]"
        ImmutableSet  | "ImmutableSet.of('a', 'b', 'c')"  | "[a, b, c]"
        ImmutableMap  | "ImmutableMap.of(1, 'a', 2, 'b')" | "[1:a, 2:b]"
    }

    def "restores task fields whose value is Serializable and has writeReplace method"() {
        buildFile << """
            class Placeholder implements Serializable {
                String value

                private Object readResolve() {
                    return new OtherBean(prop: "[\$value]")
                }
            }

            class OtherBean implements Serializable {
                String prop

                private Object writeReplace() {
                    return new Placeholder(value: prop)
                }
            }

            class SomeBean {
                OtherBean value
            }

            class SomeTask extends DefaultTask {
                private final SomeBean bean = new SomeBean()
                private final OtherBean value

                SomeTask() {
                    value = new OtherBean(prop: 'a')
                    bean.value = new OtherBean(prop: 'b')
                }

                @TaskAction
                void run() {
                    println "this.value = " + value.prop
                    println "bean.value = " + bean.value.prop
                }
            }

            task ok(type: SomeTask)
        """

        when:
        instantRun "ok"
        instantRun "ok"

        then:
        outputContains("this.value = [a]")
        outputContains("bean.value = [b]")
    }

    def "restores task fields whose value is Serializable and has only a writeObject method"() {
        buildFile << """
            class SomeBean implements Serializable {
                String value

                private void writeObject(java.io.ObjectOutputStream oos) {
                    value = "42"
                    oos.defaultWriteObject()
                }
            }

            class SomeTask extends DefaultTask {
                private final SomeBean bean = new SomeBean()

                @TaskAction
                void run() {
                    println "bean.value = " + bean.value
                }
            }

            task ok(type: SomeTask)
        """

        when:
        instantRun "ok"

        then: "bean is serialized before task runs"
        outputContains("bean.value = 42")

        when:
        instantRun "ok"

        then:
        outputContains("bean.value = 42")
    }

    @Unroll
    def "restores task fields whose value is service of type #type"() {
        buildFile << """
            class SomeBean {
                ${type} value
            }

            class SomeTask extends DefaultTask {
                @Internal
                final SomeBean bean = new SomeBean()
                @Internal
                ${type} value

                @TaskAction
                void run() {
                    value.${invocation}
                    bean.value.${invocation}
                }
            }

            task ok(type: SomeTask) {
                value = ${reference}
                bean.value = ${reference}
            }
        """

        when:
        instantRun "ok"
        instantRun "ok"

        then:
        noExceptionThrown()

        where:
        type                             | reference                                                   | invocation
        Logger.name                      | "logger"                                                    | "info('hi')"
        ObjectFactory.name               | "objects"                                                   | "newInstance(SomeBean)"
        ToolingModelBuilderRegistry.name | "project.services.get(${ToolingModelBuilderRegistry.name})" | "toString()"
        WorkerExecutor.name              | "project.services.get(${WorkerExecutor.name})"              | "noIsolation()"
        FileSystemOperations.name        | "project.services.get(${FileSystemOperations.name})"        | "toString()"
        ExecOperations.name              | "project.services.get(${ExecOperations.name})"              | "toString()"
        ListenerManager.name             | "project.services.get(${ListenerManager.name})"             | "toString()"
        JavaInstallationRegistry.name    | "project.services.get(${JavaInstallationRegistry.name})"    | "installationForCurrentVirtualMachine"
    }

    @Unroll
    def "restores task fields whose value is provider of type #type"() {
        buildFile << """
            import ${Inject.name}

            class SomeBean {
                ${type} value
            }

            class SomeTask extends DefaultTask {
                @Internal
                final SomeBean bean = project.objects.newInstance(SomeBean)
                @Internal
                ${type} value

                @TaskAction
                void run() {
                    println "this.value = " + value.getOrNull()
                    println "bean.value = " + bean.value.getOrNull()
                }
            }

            task ok(type: SomeTask) {
                value = ${reference}
                bean.value = ${reference}
            }
        """

        when:
        instantRun "ok"
        instantRun "ok"

        then:
        outputContains("this.value = ${output}")
        outputContains("bean.value = ${output}")

        where:
        type               | reference                                 | output
        "Provider<String>" | "providers.provider { 'value' }"          | "value"
        "Provider<String>" | "providers.provider { null }"             | "null"
        "Provider<String>" | "objects.property(String).value('value')" | "value"
        "Provider<String>" | "objects.property(String)"                | "null"
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
        instantRun "ok"

        then:
        outputContains("calculating value")
        outputContains("this.value = value")

        when:
        instantRun "ok"

        then:
        outputDoesNotContain("calculating value")
        outputContains("this.value = value")
    }

    @Unroll
    def "restores task fields whose value is broken #type"() {
        def instantExecution = newInstantExecutionFixture()

        buildFile << """
            import ${Inject.name}

            class SomeTask extends DefaultTask {
                @Internal
                ${type} value = ${reference} { throw new RuntimeException("broken!") }

                @TaskAction
                void run() {
                    println "this.value = " + value.${query}
                }
            }

            task broken(type: SomeTask) {
            }
        """

        when:
        instantFails "broken"
        instantFails "broken"

        then:
        instantExecution.assertStateLoaded()
        failure.assertTasksExecuted(":broken")
        failure.assertHasDescription("Execution failed for task ':broken'.")
        failure.assertHasCause("broken!")

        where:
        type               | reference                    | query
        "Provider<String>" | "project.providers.provider" | "get()"
        "FileCollection"   | "project.files"              | "files"
    }

    @Unroll
    def "restores task fields whose value is property of type #type"() {
        buildFile << """
            import ${Inject.name}

            class SomeBean {
                @Internal
                final ${type} value

                @Inject
                SomeBean(ObjectFactory objects) {
                    value = ${factory}
                }
            }

            class SomeTask extends DefaultTask {
                @Internal
                final SomeBean bean = project.objects.newInstance(SomeBean)
                @Internal
                final ${type} value

                @Inject
                SomeTask(ObjectFactory objects) {
                    value = ${factory}
                }

                @TaskAction
                void run() {
                    println "this.value = " + value.getOrNull()
                    println "bean.value = " + bean.value.getOrNull()
                }
            }

            task ok(type: SomeTask) {
                value = ${reference}
                bean.value = ${reference}
            }
        """

        when:
        instantRun "ok"
        instantRun "ok"

        then:
        def expected = output instanceof File ? file(output.path) : output
        outputContains("this.value = ${expected}")
        outputContains("bean.value = ${expected}")

        where:
        type                          | factory                               | reference        | output
        "Property<String>"            | "objects.property(String)"            | "'value'"        | "value"
        "Property<String>"            | "objects.property(String)"            | "null"           | "null"
        "DirectoryProperty"           | "objects.directoryProperty()"         | "file('abc')"    | new File('abc')
        "DirectoryProperty"           | "objects.directoryProperty()"         | "null"           | "null"
        "RegularFileProperty"         | "objects.fileProperty()"              | "file('abc')"    | new File('abc')
        "RegularFileProperty"         | "objects.fileProperty()"              | "null"           | "null"
        "ListProperty<String>"        | "objects.listProperty(String)"        | "[]"             | "[]"
        "ListProperty<String>"        | "objects.listProperty(String)"        | "['abc']"        | ['abc']
        "SetProperty<String>"         | "objects.setProperty(String)"         | "[]"             | "[]"
        "SetProperty<String>"         | "objects.setProperty(String)"         | "['abc']"        | ['abc']
        "MapProperty<String, String>" | "objects.mapProperty(String, String)" | "[:]"            | [:]
        "MapProperty<String, String>" | "objects.mapProperty(String, String)" | "['abc': 'def']" | ['abc': 'def']
    }

    @Unroll
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
        instantRun "ok"
        instantRun "ok"

        then:
        outputContains("value = ${file("dir1")}")
        outputContains("value.child = ${file("dir1/child")}")
        outputContains("propValue = ${file("dir2")}")
        outputContains("propValue.child = ${file("dir2/child")}")
        outputContains("propValue.child.mapped = ${file("dir2/child")}")
    }

    @Unroll
    def "restores task fields whose value is FileCollection"() {
        buildFile << """
            import ${Inject.name}

            class SomeBean {
                @Internal
                final FileCollection value

                @Inject
                SomeBean(ProjectLayout layout) {
                    value = ${factory}
                }
            }

            class SomeTask extends DefaultTask {
                @Internal
                final SomeBean bean = project.objects.newInstance(SomeBean)
                @Internal
                final FileCollection value

                @Inject
                SomeTask(ProjectLayout layout) {
                    value = ${factory}
                }

                @TaskAction
                void run() {
                    println "this.value = " + value.files
                    println "bean.value = " + bean.value.files
                }
            }

            task ok(type: SomeTask) {
            }
        """

        when:
        instantRun "ok"
        instantRun "ok"

        then:
        def expected = output.collect { file(it) }
        outputContains("this.value = ${expected}")
        outputContains("bean.value = ${expected}")

        where:
        factory                  | output
        "layout.files()"         | []
        "layout.files('a', 'b')" | ['a', 'b']
    }

    @Unroll
    def "restores task fields whose value is a serializable #kind Java lambda"() {
        given:
        file("buildSrc/src/main/java/my/LambdaTask.java").tap {
            parentFile.mkdirs()
            text = """
                package my;

                import org.gradle.api.*;
                import org.gradle.api.tasks.*;

                public class LambdaTask extends DefaultTask {

                    public interface SerializableSupplier<T> extends java.io.Serializable {
                        T get();
                    }

                    private SerializableSupplier<Integer> supplier;

                    public void setSupplier(SerializableSupplier<Integer> supplier) {
                        this.supplier = supplier;
                    }

                    public void setNonInstanceCapturingLambda() {
                        final int i = getName().length();
                        setSupplier(() -> i);
                    }

                    public void setInstanceCapturingLambda() {
                        setSupplier(() -> getName().length());
                    }

                    @TaskAction
                    void printValue() {
                        System.out.println("this.supplier.get() -> " + this.supplier.get());
                    }
                }
            """
        }

        buildFile << """
            task ok(type: my.LambdaTask) {
                $expression
            }
        """

        when:
        instantRun "ok"
        instantRun "ok"

        then:
        outputContains("this.supplier.get() -> 2")

        where:
        kind                     | expression
        "instance capturing"     | "setInstanceCapturingLambda()"
        "non-instance capturing" | "setNonInstanceCapturingLambda()"
    }

    @Unroll
    def "restores task fields whose value is #kind TextResource"() {

        given:
        file("resource.txt") << 'content'
        createZip("resource.zip") {
            file("resource.txt") << 'content'
        }

        and:
        buildFile << """

            class SomeTask extends DefaultTask {

                @Input
                TextResource textResource = project.resources.text.$expression

                @TaskAction
                def action() {
                    println('> ' + textResource.asString())
                }
            }

            tasks.register("someTask", SomeTask)
        """

        when:
        instantRun 'someTask'

        then:
        outputContains("> content")

        when:
        instantRun 'someTask'

        then:
        outputContains("> content")

        where:
        kind               | expression
        'a string'         | 'fromString("content")'
        'a file'           | 'fromFile("resource.txt")'
        'an uri'           | 'fromUri(project.uri(project.file("resource.txt")))'
        'an insecure uri'  | 'fromInsecureUri(project.uri(project.file("resource.txt")))'
        'an archive entry' | 'fromArchiveEntry("resource.zip", "resource.txt")'
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
        instantRun "ok"
        instantRun "ok"

        then:
        outputContains("this.value = 42")
        outputContains("this.unused = null")
        outputContains("this.bean.value = 42")
        outputContains("this.bean.unused = null")
        outputContains("this.unusedBean.value = null")
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
        instantRun "ok"
        instantRun "ok"

        then:
        outputContains("thisTask = true")
        outputContains("bean.owner = true")
    }
}
