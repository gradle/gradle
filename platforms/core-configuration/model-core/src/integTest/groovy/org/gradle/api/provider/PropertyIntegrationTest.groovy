/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

class PropertyIntegrationTest extends AbstractIntegrationSpec {
    def "can use property as task input"() {
        given:
        taskTypeWritesPropertyValueToFile()
        buildFile << """

task thing(type: SomeTask) {
    prop = providers.systemProperty('prop')
    outputFile = layout.buildDirectory.file("out.txt")
}

"""

        when:
        executer.withArgument("-Dprop=123")
        run("thing")

        then:
        executedAndNotSkipped(":thing")

        when:
        executer.withArgument("-Dprop=123")
        run("thing")

        then:
        skipped(":thing")

        when:
        executer.withArgument("-Dprop=abc")
        run("thing")

        then:
        executedAndNotSkipped(":thing")

        when:
        executer.withArgument("-Dprop=abc")
        run("thing")

        then:
        skipped(":thing")
    }

    def "can define task with abstract Property<#type> getter"() {
        given:
        buildFile << """
            class Param<T> {
                T display
                String toString() { display.toString() }
            }

            abstract class MyTask extends DefaultTask {
                @Input
                abstract Property<$type> getProp()

                @TaskAction
                void go() {
                    println("prop = \${prop.get()}")
                }
            }

            tasks.create("thing", MyTask) {
                prop = $value
            }
        """

        when:
        succeeds("thing")

        then:
        outputContains("prop = $display")

        where:
        type            | value                               | display
        "String"        | "'abc'"                             | "abc"
        "Param<String>" | "new Param<String>(display: 'abc')" | "abc"
    }

    def "can define task with abstract nested property"() {
        given:
        buildFile << """
            interface NestedType {
                @Input
                Property<String> getProp()
            }

            abstract class MyTask extends DefaultTask {
                @Nested
                abstract NestedType getNested()

                void nested(Action<NestedType> action) {
                    action.execute(nested)
                }

                @TaskAction
                void go() {
                    println("prop = \${nested.prop.get()}")
                }
            }

            tasks.create("thing", MyTask) {
                nested {
                    prop = "value"
                }
            }
        """

        when:
        succeeds("thing")

        then:
        outputContains("prop = value")
    }

    def "fails when property with no value is queried"() {
        given:
        buildFile << """
            abstract class SomeTask extends DefaultTask {
                @Internal
                abstract Property<String> getProp()

                @TaskAction
                def go() {
                    prop.get()
                }
            }

            tasks.register('thing', SomeTask)
        """

        when:
        fails("thing")

        then:
        failure.assertHasDescription("Execution failed for task ':thing'.")
        failure.assertHasCause("Cannot query the value of task ':thing' property 'prop' because it has no value available.")
    }

    @Requires(
        value = IntegTestPreconditions.NotConfigCached,
        reason = "Config cache does not support extensions during execution, so cause does not include any provenance information"
    )
    def "fails when property with no value because source property has no value is queried"() {
        given:
        buildFile << """
            interface SomeExtension {
                Property<String> getSource()
            }

            abstract class SomeTask extends DefaultTask {
                @Internal
                abstract Property<String> getProp()

                @TaskAction
                def go() {
                    prop.get()
                }
            }

            def custom1 = extensions.create('custom1', SomeExtension)

            def custom2 = extensions.create('custom2', SomeExtension)
            custom2.source = custom1.source

            tasks.register('thing', SomeTask) {
                prop = custom2.source
            }
        """

        when:
        fails("thing")

        then:
        failure.assertHasDescription("Execution failed for task ':thing'.")
        failure.assertHasCause("""Cannot query the value of task ':thing' property 'prop' because it has no value available.
The value of this property is derived from:
  - extension 'custom2' property 'source'
  - extension 'custom1' property 'source'""")
    }

    def "can use property with no value as optional ad hoc task input property"() {
        given:
        buildFile << """

def prop = project.objects.property(String)

task thing {
    inputs.property("prop", prop).optional(true)
    doLast {
        println "prop = " + prop.getOrNull()
    }
}
"""

        when:
        run("thing")

        then:
        output.contains("prop = null")
    }

    @ToBeFixedForConfigurationCache(because = "gradle/configuration-cache#268")
    def "reports failure due to broken @Input task property"() {
        taskTypeWritesPropertyValueToFile()
        buildFile << """

task thing(type: SomeTask) {
    prop = providers.provider { throw new RuntimeException("broken") }
    outputFile = layout.buildDirectory.file("out.txt")
}

        """

        when:
        fails("thing")

        then:
        failure.assertHasDescription("Execution failed for task ':thing'.")
        failure.assertHasCause("Failed to calculate the value of task ':thing' property 'prop'.")
        failure.assertHasCause("broken")
    }

    @ToBeFixedForConfigurationCache(because = "configuration cache captures provider value")
    def "task @Input property calculation is called once only when task executes"() {
        taskTypeWritesPropertyValueToFile()
        buildFile << """

task thing(type: SomeTask) {
    prop = providers.provider {
        println("calculating value")
        return "value"
    }
    outputFile = layout.buildDirectory.file("out.txt")
}

        """

        when:
        run("thing")

        then:
        output.count("calculating value") == 1

        when:
        run("thing")

        then:
        result.assertTaskSkipped(":thing")
        output.count("calculating value") == 1

        when:
        run("help")

        then:
        output.count("calculating value") == 0
    }

    @ToBeFixedForConfigurationCache(because = "gradle/configuration-cache#270")
    def "does not calculate task @Input property value when task is skipped due to @SkipWhenEmpty on another property"() {
        buildFile << """

class SomeTask extends DefaultTask {
    @Input
    final Property<String> prop = project.objects.property(String)

    @InputFiles @SkipWhenEmpty
    final SetProperty<RegularFile> outputFile = project.objects.setProperty(RegularFile)

    @TaskAction
    void go() {
    }
}

task thing(type: SomeTask) {
    prop = providers.provider {
        throw new RuntimeException("should not be called")
    }
}

        """

        when:
        run("thing")

        then:
        result.assertTaskSkipped(":thing")
    }

    def "can set property value from DSL using a value or a provider"() {
        given:
        buildFile << """
class SomeExtension {
    final Property<String> prop

    @javax.inject.Inject
    SomeExtension(ObjectFactory objects) {
        prop = objects.property(String)
    }
}

class SomeTask extends DefaultTask {
    final Property<String> prop = project.objects.property(String)
}

extensions.create('custom', SomeExtension)
custom.prop = "value"
assert custom.prop.get() == "value"

custom.prop = providers.provider { "new value" }
assert custom.prop.get() == "new value"

tasks.create('t', SomeTask)
tasks.t.prop = custom.prop
assert tasks.t.prop.get() == "new value"

custom.prop = "changed"
assert custom.prop.get() == "changed"
assert tasks.t.prop.get() == "changed"

"""

        expect:
        succeeds()
    }

    def "can set String property value using a GString"() {
        given:
        buildFile << """
class SomeExtension {
    final Property<String> prop

    @javax.inject.Inject
    SomeExtension(ObjectFactory objects) {
        prop = objects.property(String)
    }
}

extensions.create('custom', SomeExtension)
custom.prop = "\${'some value 1'.substring(5)}"
assert custom.prop.get() == "value 1"

custom.prop = providers.provider { "\${'some value 2'.substring(5)}" }
assert custom.prop.get() == "value 2"

custom.prop = null
custom.prop.convention("\${'some value 3'.substring(5)}")
assert custom.prop.get() == "value 3"

custom.prop.convention(providers.provider { "\${'some value 4'.substring(5)}" })
assert custom.prop.get() == "value 4"
"""

        expect:
        succeeds()
    }

    @Requires(
        value = IntegTestPreconditions.NotConfigCached,
        reason = "Config cache does not support extensions during execution, leading to 'Could not get unknown property 'custom' for task ':wrongValueTypeDsl' of type org.gradle.api.DefaultTask."
    )
    def "reports failure to set property value using incompatible type"() {
        given:
        buildFile << """
class SomeExtension {
    final Property<String> prop

    @javax.inject.Inject
    SomeExtension(ObjectFactory objects) {
        prop = objects.property(String)
    }
}

extensions.create('custom', SomeExtension)

task wrongValueTypeDsl {
    doLast {
        custom.prop = 123
    }
}

task wrongValueTypeApi {
    doLast {
        custom.prop.set(123)
    }
}

task wrongPropertyTypeDsl {
    doLast {
        custom.prop = objects.property(Integer)
    }
}

task wrongPropertyTypeApi {
    doLast {
        custom.prop.set(objects.property(Integer))
    }
}

task wrongRuntimeType {
    doLast {
        custom.prop = providers.provider { 123 }
        custom.prop.get()
    }
}

task wrongConventionValueType {
    doLast {
        custom.prop.convention(123)
    }
}

task wrongConventionPropertyType {
    doLast {
        custom.prop.convention(objects.property(Integer))
    }
}

task wrongConventionRuntimeValueType {
    doLast {
        custom.prop.convention(providers.provider { 123 })
        custom.prop.get()
    }
}
"""

        when:
        fails("wrongValueTypeDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongValueTypeDsl'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type java.lang.String using an instance of type java.lang.Integer.")

        when:
        fails("wrongValueTypeApi")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongValueTypeApi'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type java.lang.String using an instance of type java.lang.Integer.")

        when:
        fails("wrongPropertyTypeDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongPropertyTypeDsl'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type java.lang.String using a provider of type java.lang.Integer.")

        when:
        fails("wrongPropertyTypeApi")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongPropertyTypeApi'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type java.lang.String using a provider of type java.lang.Integer.")

        when:
        fails("wrongRuntimeType")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongRuntimeType'.")
        failure.assertHasCause("Cannot get the value of extension 'custom' property 'prop' of type java.lang.String as the provider associated with this property returned a value of type java.lang.Integer.")

        when:
        fails("wrongConventionValueType")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongConventionValueType'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type java.lang.String using an instance of type java.lang.Integer.")

        when:
        fails("wrongConventionPropertyType")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongConventionPropertyType'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type java.lang.String using a provider of type java.lang.Integer.")

        when:
        fails("wrongConventionRuntimeValueType")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongConventionRuntimeValueType'.")
        failure.assertHasCause("Cannot get the value of extension 'custom' property 'prop' of type java.lang.String as the provider associated with this property returned a value of type java.lang.Integer.")
    }

    def "fails when specialized factory method is not used"() {
        buildFile << """
class SomeExtension {
    final Property<List<String>> prop1
    final Property<Set<String>> prop2
    final Property<Directory> prop3
    final Property<RegularFile> prop4
    final Property<Map<String, String>> prop5

    @javax.inject.Inject
    SomeExtension(ObjectFactory objects) {
        $prop = objects.property($type)
    }
}

project.extensions.create("some", SomeExtension)
        """

        when:
        fails()

        then:
        failure.assertHasCause("Please use the ObjectFactory.$method method to create a property of type $type$typeParam.")

        where:
        prop    | method                | type          | typeParam
        'prop1' | 'listProperty()'      | 'List'        | '<T>'
        'prop2' | 'setProperty()'       | 'Set'         | '<T>'
        'prop3' | 'mapProperty()'       | 'Map'         | '<K, V>'
        'prop4' | 'directoryProperty()' | 'Directory'   | ''
        'prop5' | 'fileProperty()'      | 'RegularFile' | ''
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    @Issue("https://github.com/gradle/gradle/issues/12811")
    def "multiple tasks can have property values calculated from a shared finalize on read property instance with value derived from dependency resolution"() {
        createDirs("producer", "consumer")
        settingsFile << """
            include 'producer'
            include 'consumer'
        """
        taskTypeWritesPropertyValueToFile()
        buildFile << """
            project(':producer') {
                def t = task producer(type: SomeTask) {
                    prop = "producer"
                    outputFile = layout.buildDirectory.file("producer.txt")
                }
                def c = configurations.create("default")
                c.outgoing.artifact(t.outputFile)

                // Start another task that blocks dependency resolution from the other project
                task slow {
                    dependsOn t
                    doLast {
                        sleep(200)
                    }
                }
            }

            interface Model {
                Property<String> getProp()
            }

            project(':consumer') {
                def m = extensions.create('model', Model)
                m.prop.finalizeValueOnRead()
                def c = configurations.create("incoming")
                dependencies.incoming(project(":producer"))
                m.prop = c.elements.map { files -> files*.asFile*.text.join(",") }
                task consumer1(type: SomeTask) {
                    prop = m.prop
                    outputFile = layout.buildDirectory.file("consumer1.txt")
                }
                task consumer2(type: SomeTask) {
                    prop = m.prop
                    outputFile = layout.buildDirectory.file("consumer2.txt")
                }
            }
        """

        when:
        run("slow", "consumer1", "consumer2", "--parallel", "--max-workers=3")

        then:
        file("consumer/build/consumer1.txt").text == "producer"
        file("consumer/build/consumer2.txt").text == "producer"
    }

    @Issue("https://github.com/gradle/gradle/issues/12969")
    @Requires(IntegTestPreconditions.NotParallelExecutor)
    def "task can have property value derived from dependency resolution result when another task has input files derived from same result"() {
        createDirs("producer", "consumer")
        settingsFile << """
            include 'producer'
            include 'consumer'
        """
        taskTypeWritesPropertyValueToFile()
        buildFile << """
            project(':producer') {
                def t = task producer(type: SomeTask) {
                    prop = "producer"
                    outputFile = layout.buildDirectory.file("producer.txt")
                }
                def c = configurations.create("default")
                c.outgoing.artifact(t.outputFile)

                // Start another task that blocks dependency resolution from the other project
                task slow {
                    dependsOn t
                    doLast {
                        sleep(200)
                    }
                }
            }

            interface Model {
                Property<String> getProp()
            }

            project(':consumer') {
                def m = extensions.create('model', Model)
                m.prop.finalizeValueOnRead()
                def c = configurations.create("incoming")
                dependencies.incoming(project(":producer"))
                m.prop = c.elements.map { files -> files*.asFile*.text.join(",") }
                task consumer1 {
                    inputs.files(c)
                    doLast {
                        println inputs.files
                    }
                }
                task consumer2(type: SomeTask) {
                    prop = m.prop
                    outputFile = layout.buildDirectory.file("consumer2.txt")
                }
            }
        """

        when:
        run("slow", "consumer1", "consumer2", "--parallel", "--max-workers=3")

        then:
        file("consumer/build/consumer2.txt").text == "producer"
    }

    def taskTypeWritesPropertyValueToFile() {
        buildFile << """
            class SomeTask extends DefaultTask {
                @Input
                final Property<String> prop = project.objects.property(String)

                @OutputFile
                final Property<RegularFile> outputFile = project.objects.fileProperty()

                @TaskAction
                void go() {
                    outputFile.get().asFile.text = prop.get()
                }
            }
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/10248#issuecomment-592528234")
    def "can use findProperty from a closure passed to ConfigureUtil.configure via an extension"() {
        when:
        buildFile << """
        class SomeExtension {
            def innerThing(Closure closure) {
                org.gradle.util.internal.ConfigureUtil.configure(closure, new InnerThing())
            }
            class InnerThing {}
        }
        extensions.create('someExtension', SomeExtension)
        someExtension {
            innerThing {
                findProperty('foo')
            }
        }
        """
        then:
        succeeds()
    }

    @Issue("https://github.com/gradle/gradle/issues/13932")
    def "does not resolve mapped property at configuration time"() {
        buildFile """
            abstract class Producer extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction
                def action() {
                    def outputFile = output.get().asFile
                    outputFile.write("some text")
                }
            }

            abstract class Consumer extends DefaultTask {
                @InputFiles
                abstract ListProperty<RegularFile> getInputFiles()
                @TaskAction
                def action() {
                    inputFiles.get().each {
                        println("action! on " + it)
                    }
                }
            }

            def producer = tasks.register("producer", Producer) {
                output = layout.projectDirectory.file("foo.txt")
            }
            tasks.register("consumer", Consumer) {
                def layout = layout
                def filtered = files(producer.map { it.output }).elements.map {
                    it.collect { it.asFile }
                        .findAll { it.isFile() }
                        .collect { layout.projectDirectory.file(it.absolutePath) }
                }
                inputFiles.set(filtered)
            }
        """

        when:
        run 'consumer'
        then:
        executedAndNotSkipped(":producer", ":consumer")
    }

    @Issue("https://github.com/gradle/gradle/issues/16775")
    def "orElse does not cause error when map-ing a task property"() {
        buildFile """
            abstract class Producer extends DefaultTask {
                @OutputDirectory
                abstract DirectoryProperty getOutput()

                @TaskAction
                def action() {
                    def outputFile = new File(output.get().asFile, "output.txt")
                    outputFile.write("some text")
                }
            }

            abstract class MyExt {
                abstract DirectoryProperty getArtifactDir()
            }

            abstract class Consumer extends DefaultTask {
                @InputFiles
                abstract ListProperty<String> getFileNames()

                @TaskAction
                def action() {
                    println("files: " + fileNames.get())
                }
            }

            def producer = tasks.register("producer", Producer) {
                output = layout.buildDirectory.dir("producer")
            }

            def myExt = extensions.create("myExt", MyExt)
            myExt.artifactDir = producer.flatMap { it.output }

            tasks.register("consumer", Consumer) {
               fileNames = myExt.artifactDir.map {
                  it.asFileTree.collect { it.absolutePath }
               }.orElse([ "else" ])
            }
        """

        when:
        run 'consumer'

        then:
        executedAndNotSkipped(":producer", ":consumer")
    }

    def "can use a mapped value provider"() {
        buildFile """
            abstract class MyTask extends DefaultTask {
                @Input
                abstract ListProperty<String> getStrings()

                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction
                def action() {
                    def outputFile = output.get().asFile
                    outputFile.write(strings.get().join(","))
                }
            }

            tasks.register("myTask", MyTask) {
                strings.add(providers.gradleProperty("my").map { it + "-prop"})
                output = layout.buildDirectory.file("myTask.txt")
            }
        """

        when:
        run 'myTask', "-Pmy=value1"
        then:
        executedAndNotSkipped(":myTask")
        file("build/myTask.txt").text == "value1-prop"

        when:
        run 'myTask', "-Pmy=value1"
        then:
        skipped(":myTask")
        file("build/myTask.txt").text == "value1-prop"
    }

    def "can depend on the output file collection containing an optional output file"() {
        buildFile """
            abstract class Producer extends DefaultTask {
                @Optional
                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction
                def action() {
                    if (output.present) {
                        def outputFile = output.get().asFile
                        outputFile.write("some text")
                    }
                }
            }

            abstract class Consumer extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getInputFiles()
                @TaskAction
                def action() {
                    inputFiles.each {
                        println("action! on " + it)
                    }
                }
            }

            def producer = tasks.register("producer", Producer)
            tasks.register("consumer", Consumer) {
                inputFiles.from(producer)
            }
        """

        when:
        run "consumer"
        then:
        executedAndNotSkipped(":producer", ":consumer")
    }

    @Issue("https://github.com/gradle/gradle/issues/13623")
    def "setter for #fieldModifier property with different type doesn't cause drop of task dependency"() {
        buildFile """
            abstract class Producer extends DefaultTask {
                private $fieldModifier DirectoryProperty foo

                @Inject
                Producer(ObjectFactory objectFactory) {
                    foo = objectFactory.directoryProperty()
                }

                @OutputDirectory
                DirectoryProperty getFoo() { return foo }

                void setFoo(File foo) { throw RuntimeException("") }

                @TaskAction
                void produce() { print("Producer ran") }
            }

            abstract class Consumer extends DefaultTask {
                @InputDirectory
                abstract DirectoryProperty getFoo()

                @TaskAction
                void consume() { print("Consumer ran") }
            }

            def producer = tasks.register('producer', Producer) {
                foo.set(project.layout.buildDir.dir('fooDir'))
            }

            tasks.register('consumer', Consumer) {
                foo = producer.flatMap { it.foo }
            }
        """
        expect:
        succeeds("consumer")
        executedAndNotSkipped(":producer", ":consumer")

        where:
        fieldModifier << ["", "final"]
    }

    def "can use a filtered value provider"() {
        enableProblemsApiCheck()

        buildFile """
            abstract class MyTask extends DefaultTask {
                @Input
                abstract ListProperty<String> getStrings()

                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction
                def action() {
                    def outputFile = output.get().asFile
                    outputFile.write(strings.get().join(","))
                }
            }

            tasks.register("myTask", MyTask) {
                strings.add(providers.gradleProperty("my").filter { it.contains("value") })
                output = layout.buildDirectory.file("myTask.txt")
            }
        """

        when:
        run 'myTask', "-Pmy=value1"
        then:
        executedAndNotSkipped(":myTask")
        file("build/myTask.txt").text == "value1"

        when:
        run 'myTask', "-Pmy=value1"
        then:
        skipped(":myTask")
        file("build/myTask.txt").text == "value1"

        when:
        fails('myTask', "-Pmy=trash")
        then:
        failureDescriptionContains("Type 'MyTask' property 'strings' doesn't have a configured value.")

        when:
        fails('myTask')
        then:
        failureDescriptionContains("Type 'MyTask' property 'strings' doesn't have a configured value.")

        verifyAll(receivedProblem) {
            fqid == 'validation:property-validation:value-not-set'
            contextualLabel == 'Type \'MyTask\' property \'strings\' doesn\'t have a configured value'
            details == 'This property isn\'t marked as optional and no value has been configured'
            solutions == [
                'Assign a value to \'strings\'',
                'Mark property \'strings\' as optional',
            ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'strings',
            ]
        }
    }

    def "filter is evaluated lazily"() {
        buildKotlinFile << """
            tasks.register("printer") {
                val someValue = objects.property<String>().convention("some value")
                doLast {
                    val filtered = someValue.filter { it.contains("value") }
                    someValue.set("trash")
                    println("filter: ${'$'}{filtered.getOrNull()}")
                }
            }
        """

        when:
        run 'printer'
        then:
        outputContains("filter: null")
    }

    def "circular evaluation of task property is detected"() {
        buildFile """
            abstract class MyTask extends DefaultTask {
                @Input
                abstract Property<String> getStringInput()

                @TaskAction
                def action() {
                    println("stringInput = \${stringInput.get()}")
                }
            }

            tasks.register("myTask", MyTask) {
                stringInput.convention("defaultValue")
                stringInput = $selfReference
            }
        """

        when:
        fails "myTask"

        then:
        failureCauseContains("Circular evaluation detected")

        where:
        selfReference                                 || _
        "stringInput"                                 || _
        "stringInput.map { it.capitalize() }"         || _
        "provider { stringInput.get().capitalize() }" || _
    }

    def "circular evaluation of standalone property is detected"() {
        buildFile """
            def prop = objects.property(String)
            prop.set(prop.map { "newValue" })

            println("prop = \${prop.get()}")

            tasks.register("myTask") {}
        """

        when:
        fails "myTask"

        then:
        failureCauseContains("Circular evaluation detected")
    }
}
