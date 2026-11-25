/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

class LazyBackedPropertyIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile """
            import org.gradle.internal.instrumentation.api.annotations.LazyProperties
            import org.gradle.internal.instrumentation.api.annotations.BackedByLazyProperty
            import static org.gradle.api.internal.provider.EagerPropertySupport.setEager

            interface ConsumerInputs {
                Property<String> getMessage()
            }

            abstract class Consumer extends DefaultTask {
                @Internal
                @LazyProperties
                abstract ConsumerInputs getLazyProperties()

                @Input
                @Optional
                @BackedByLazyProperty("lazyProperties.message")
                String getMessage() {
                    return lazyProperties.message.getOrNull()
                }

                void setMessage(String value) {
                    setEager(lazyProperties.message, value)
                }

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    println("message: " + getMessage())
                    outputFile.get().asFile.text = String.valueOf(getMessage())
                }
            }

            abstract class Producer extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = "produced by " + name
                }
            }

            def producer = tasks.register("producer", Producer) {
                outputFile = layout.buildDirectory.file("producer.txt")
            }
        """
    }

    def "value input bound to a mapped task output runs the producer first"() {
        buildFile """
            tasks.register("consumer", Consumer) {
                lazyProperties.message = producer.flatMap { it.outputFile }.map { it.asFile.text }
                outputFile = layout.buildDirectory.file("consumer.txt")
            }
        """

        when:
        succeeds("consumer")

        then:
        result.assertTasksScheduledInOrder(":producer", ":consumer")
        outputContains("message: produced by producer")
        file("build/consumer.txt").text == "produced by producer"
    }

    def "only the annotation adds the dependency of the lazy property (annotated: #annotated)"() {
        buildFile """
            abstract class UnannotatedConsumer extends DefaultTask {
                @Internal
                @LazyProperties
                abstract ConsumerInputs getLazyProperties()

                @Input
                String getMessage() {
                    return lazyProperties.message.get()
                }

                @TaskAction
                void run() {
                    println("message: " + getMessage())
                }
            }

            tasks.register("consumer", ${annotated ? "Consumer" : "UnannotatedConsumer"}) {
                lazyProperties.message = producer.map { "from " + it.name }
                ${annotated ? 'outputFile = layout.buildDirectory.file("consumer.txt")' : ''}
            }
        """

        when:
        succeeds("consumer")

        then:
        result.assertTasksExecuted(expectedTasks)
        outputContains("message: from producer")

        where:
        annotated | expectedTasks
        true      | [":producer", ":consumer"]
        false     | [":consumer"]
    }

    def "the lazy properties accessor adds no input property"() {
        buildFile """
            abstract class PlainConsumer extends DefaultTask {
                private String message

                @Input
                @Optional
                String getMessage() {
                    return message
                }

                void setMessage(String value) {
                    message = value
                }

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = String.valueOf(message)
                }
            }

            [lazy: Consumer, plain: PlainConsumer].each { name, type ->
                tasks.register(name, type) {
                    message = "value"
                    outputFile = layout.buildDirectory.file(name + ".txt")
                    doLast {
                        println(name + " inputs: " + inputs.properties.keySet().sort())
                    }
                }
            }
        """

        when:
        succeeds("lazy", "plain")

        then:
        outputContains("lazy inputs: [message]")
        outputContains("plain inputs: [message]")
    }

    def "fixed and missing values add no dependency"() {
        buildFile """
            tasks.register("fixed", Consumer) {
                message = "fixed value"
                outputFile = layout.buildDirectory.file("fixed.txt")
            }
            tasks.register("missing", Consumer) {
                outputFile = layout.buildDirectory.file("missing.txt")
            }
        """

        when:
        succeeds("fixed", "missing")

        then:
        result.assertTasksExecuted(":fixed", ":missing")
        outputContains("message: fixed value")
        outputContains("message: null")
    }

    def "override with @Internal removes the dependency"() {
        buildFile """
            abstract class InternalConsumer extends Consumer {
                @Override
                @Internal
                String getMessage() {
                    return super.getMessage()
                }
            }

            tasks.register("consumer", InternalConsumer) {
                lazyProperties.message = producer.map { "from " + it.name }
                outputFile = layout.buildDirectory.file("consumer.txt")
            }
        """

        when:
        succeeds("consumer")

        then:
        result.assertTasksExecuted(":consumer")
        outputContains("message: from producer")
    }

    def "file input #type keeps the dependency of the lazy property"() {
        buildFile """
            interface FileInputs {
                RegularFileProperty getConfigFile()
            }

            abstract class FileConsumer extends DefaultTask {
                @Internal
                @LazyProperties
                abstract FileInputs getLazyProperties()

                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                @BackedByLazyProperty("lazyProperties.configFile")
                File getConfigFile() {
                    def file = lazyProperties.configFile.getOrNull()
                    return file == null ? null : file.asFile
                }

                void setConfigFile(File value) {
                    lazyProperties.configFile.fileValue(value)
                }

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = "consumed " + getConfigFile().text
                }
            }

            abstract class RelativeFileConsumer extends FileConsumer {
                @Override
                @PathSensitive(PathSensitivity.RELATIVE)
                File getConfigFile() {
                    return super.getConfigFile()
                }
            }

            tasks.register("consumer", $type) {
                lazyProperties.configFile = producer.flatMap { it.outputFile }
                outputFile = layout.buildDirectory.file("consumer.txt")
            }
        """

        when:
        succeeds("consumer")

        then:
        result.assertTasksScheduledInOrder(":producer", ":consumer")
        file("build/consumer.txt").text == "consumed produced by producer"

        where:
        type                   | _
        "FileConsumer"         | _
        "RelativeFileConsumer" | _
    }

    def "subclass can narrow the type of the lazy object"() {
        buildFile """
            interface SubConsumerInputs extends ConsumerInputs {
                Property<String> getExtra()
            }

            abstract class SubConsumer extends Consumer {
                @Override
                @Internal
                @LazyProperties
                abstract SubConsumerInputs getLazyProperties()

                @Input
                @BackedByLazyProperty("lazyProperties.extra")
                String getExtra() {
                    return lazyProperties.extra.getOrNull()
                }

                @TaskAction
                void printExtra() {
                    println("extra: " + getExtra())
                }
            }

            def otherProducer = tasks.register("otherProducer", Producer) {
                outputFile = layout.buildDirectory.file("other.txt")
            }

            tasks.register("consumer", SubConsumer) {
                lazyProperties.message = producer.map { "from " + it.name }
                lazyProperties.extra = otherProducer.map { "from " + it.name }
                outputFile = layout.buildDirectory.file("consumer.txt")
            }
        """

        when:
        succeeds("consumer")

        then:
        result.assertTasksExecuted(":producer", ":otherProducer", ":consumer")
        outputContains("message: from producer")
        outputContains("extra: from otherProducer")
    }

    def "single segment path can point to a protected lazy getter on the same object"() {
        buildFile """
            abstract class ProtectedConsumer extends DefaultTask {
                @Internal
                protected abstract Property<String> getMessageProperty()

                @Input
                @BackedByLazyProperty("messageProperty")
                String getMessage() {
                    return messageProperty.get()
                }

                void bindMessage(Provider<String> value) {
                    messageProperty.set(value)
                }

                @TaskAction
                void run() {
                    println("message: " + getMessage())
                }
            }

            tasks.register("consumer", ProtectedConsumer) {
                bindMessage(producer.map { "from " + it.name })
            }
        """

        when:
        succeeds("consumer")

        then:
        result.assertTasksExecuted(":producer", ":consumer")
        outputContains("message: from producer")
    }

    def "owner can create the lazy object with ObjectFactory.newInstance"() {
        buildFile """
            abstract class NewInstanceConsumer extends DefaultTask {
                private final ConsumerInputs lazyProperties

                @javax.inject.Inject
                NewInstanceConsumer(ObjectFactory objects) {
                    lazyProperties = objects.newInstance(ConsumerInputs)
                }

                @Internal
                ConsumerInputs getLazyProperties() {
                    return lazyProperties
                }

                @Input
                @BackedByLazyProperty("lazyProperties.message")
                String getMessage() {
                    return lazyProperties.message.get()
                }

                @TaskAction
                void run() {
                    println("message: " + getMessage())
                }
            }

            tasks.register("consumer", NewInstanceConsumer) {
                lazyProperties.message = producer.map { "from " + it.name }
            }
        """

        when:
        succeeds("consumer")

        then:
        result.assertTasksExecuted(":producer", ":consumer")
        outputContains("message: from producer")
    }

    def "convention mapping #description"() {
        buildFile """
            def conventionValue = "convention"
            tasks.register("consumer", Consumer) {
                outputFile = layout.buildDirectory.file("consumer.txt")
                $configuration
            }
        """

        when:
        succeeds("consumer")

        then:
        outputContains("message: $expected")

        where:
        description                                        | expected      | configuration
        "is visible through the getter"                    | "convention"  | 'conventionMapping.map("message") { conventionValue }'
        "loses against a direct set on the lazy property"  | "direct"      | 'conventionMapping.map("message") { conventionValue }; lazyProperties.message = "direct"'
        "loses against a direct set made before mapping"   | "direct"      | 'lazyProperties.message = "direct"; conventionMapping.map("message") { conventionValue }'
        "loses against the eager setter"                   | "eager"       | 'conventionMapping.map("message") { conventionValue }; message = "eager"'
        "loses against the eager setter called before"     | "eager"       | 'message = "eager"; conventionMapping.map("message") { conventionValue }'
        "is evaluated lazily"                              | "changed"     | 'conventionMapping.map("message") { conventionValue }; conventionValue = "changed"'
        "is hidden by setX(null) called after mapping"     | "null"        | 'conventionMapping.map("message") { conventionValue }; message = null'
        "is hidden by setX(null) called before mapping"    | "null"        | 'message = null; conventionMapping.map("message") { conventionValue }'
        "comes back after unset() on the lazy property"    | "convention"  | 'conventionMapping.map("message") { conventionValue }; message = null; lazyProperties.message.unset()'
        "comes back after set(null) on the lazy property"  | "convention"  | 'message = "eager"; conventionMapping.map("message") { conventionValue }; lazyProperties.message.set((String) null)'
    }

    def "convention mapping applies to the lazy property"() {
        buildFile """
            tasks.register("consumer", Consumer) {
                outputFile = layout.buildDirectory.file("consumer.txt")
                conventionMapping.map("message") { "convention" }
            }
            tasks.register("printLazy") {
                def lazyMessage = tasks.named("consumer", Consumer).flatMap { it.lazyProperties.message }
                doLast {
                    println("lazy: " + lazyMessage.getOrNull())
                }
            }
        """

        when:
        succeeds("printLazy")

        then:
        outputContains("lazy: convention")
    }

    def "eager setter can change the value at execution time without a deprecation warning"() {
        buildFile """
            tasks.register("consumer", Consumer) {
                message = "configured"
                outputFile = layout.buildDirectory.file("consumer.txt")
                doFirst {
                    message = "changed"
                }
            }
        """

        when:
        succeeds("consumer")

        then:
        outputContains("message: changed")
        file("build/consumer.txt").text == "changed"
    }

    def "lazy property can be changed directly at execution time without a deprecation warning"() {
        buildFile """
            tasks.register("consumer", Consumer) {
                message = "configured"
                outputFile = layout.buildDirectory.file("consumer.txt")
                doFirst {
                    lazyProperties.message = "changed"
                }
            }
        """

        when:
        succeeds("consumer")

        then:
        outputContains("message: changed")
        file("build/consumer.txt").text == "changed"
    }

    def "wrong path fails with a clear error (#path)"() {
        buildFile """
            abstract class BrokenConsumer extends DefaultTask {
                @Internal
                @LazyProperties
                abstract ConsumerInputs getLazyProperties()

                @Input
                @BackedByLazyProperty("$path")
                String getMessage() {
                    return lazyProperties.message.get()
                }

                @TaskAction
                void run() {
                    println("message: " + getMessage())
                }
            }

            tasks.register("consumer", BrokenConsumer) {
                lazyProperties.message = "value"
            }
        """

        when:
        fails("consumer")

        then:
        failure.assertHasCause("Cannot resolve @BackedByLazyProperty(\"$path\") of BrokenConsumer.getMessage(): $reason.")

        where:
        path                      | reason
        "lazyProperties.missing"  | "segment 'missing' has no getter getMissing() or isMissing() on type 'ConsumerInputs'"
        "lazyProperties"          | "the path ends at type 'ConsumerInputs', which is not a Provider or a configurable value"
        "lazyProperties..message" | "the path has an empty segment"
    }

    @Requires(value = TestExecutionPreconditions.NotConfigCached, reason = "handles the configuration cache explicitly")
    def "changing a bound Gradle property reruns the task and reuses the configuration cache entry"() {
        buildFile """
            tasks.register("consumer", Consumer) {
                lazyProperties.message = providers.gradleProperty("msg")
                outputFile = layout.buildDirectory.file("consumer.txt")
            }
        """

        when:
        succeeds("consumer", "-Pmsg=one", "--configuration-cache")

        then:
        outputContains("Calculating task graph as no cached configuration is available")
        outputContains("message: one")

        when:
        succeeds("consumer", "-Pmsg=two", "--configuration-cache")

        then:
        outputContains("Reusing configuration cache.")
        result.assertTasksExecuted(":consumer")
        result.assertTasksSkipped()
        outputContains("message: two")
        file("build/consumer.txt").text == "two"

        when:
        succeeds("consumer", "-Pmsg=two", "--configuration-cache")

        then:
        outputContains("Reusing configuration cache.")
        result.assertTaskSkipped(":consumer")
    }
}
