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
import spock.lang.Issue

/**
 * A self-reference derives a property's new value from its current value, for example {@code p.set(p.map { ... })}.
 * Reads of the property found in the argument of {@code set()} mean the previous value of the property.
 * When the property has no explicit value, the previous value is a live read of the convention.
 */
class PropertySelfReferenceIntegrationTest extends AbstractIntegrationSpec {

    private void buildFileWithTask(String taskType, String configuration) {
        buildFile """
            abstract class MyTask extends DefaultTask {
                $taskType

                @TaskAction
                void run() {
                    println("Result: " + result())
                }
            }

            tasks.register("myTask", MyTask) {
                $configuration
            }
        """
    }

    private static String stringProperty() {
        """
            @Internal
            abstract Property<String> getMessage()

            String result() { message.getOrElse("missing") }
        """
    }

    private static String listProperty() {
        """
            @Internal
            abstract ListProperty<String> getArgs()

            String result() { args.getOrElse(["missing"]).toString() }
        """
    }

    private static String mapProperty() {
        """
            @Internal
            abstract MapProperty<String, String> getEntries()

            String result() { entries.getOrElse([missing: "missing"]).toString() }
        """
    }

    private static String fileCollection() {
        """
            @Internal
            abstract ConfigurableFileCollection getClasspath()

            String result() { classpath.files.collect { it.name }.toString() }
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/27227")
    def "property can be set from its previous value using #description"() {
        buildFileWithTask(stringProperty(), """
            message.set("foo")
            $statement
        """)

        when:
        run "myTask"

        then:
        outputContains("Result: $result")

        where:
        description             | statement                                                      | result
        "set(map)"              | 'message.set(message.map { "!! $it !!" })'                     | "!! foo !!"
        "assignment"            | 'message = message.map { "!! $it !!" }'                        | "!! foo !!"
        "value(map)"            | 'message.value(message.map { "!! $it !!" })'                   | "!! foo !!"
        "flatMap"               | 'message.set(message.flatMap { p -> provider { "$p!" } })'     | "foo!"
        "zip with itself"       | 'message.set(message.zip(message) { a, b -> a + b })'          | "foofoo"
        "filter that matches"   | 'message.set(message.filter { it.startsWith("f") })'           | "foo"
        "filter that rejects"   | 'message.set(message.filter { it.startsWith("x") })'           | "missing"
        "orElse on self"        | 'message.set(message.orElse("hello"))'                         | "foo"
        "repeated assignments"  | 'message.set(message.map { "[$it]" }); message = message.map { "$it!" }' | "[foo]!"
        "later plain set wins"  | 'message.set(message.map { "[$it]" }); message = "bar"'        | "bar"
    }

    def "direct self assignment is a no-op and keeps the convention live"() {
        buildFileWithTask(stringProperty(), """
            message.convention("hello")
            message.set(message)
            message = message
            message.convention("goodbye")
        """)

        when:
        run "myTask"

        then:
        outputContains("Result: goodbye")
    }

    @Issue("https://github.com/gradle/gradle/issues/32300")
    def "self reference reads the convention live (#description)"() {
        buildFileWithTask(stringProperty(), statement)

        when:
        run "myTask"

        then:
        outputContains("Result: $result")

        where:
        description                       | statement                                                                                                 | result
        "convention set before"           | 'message.convention("hello"); message = message.map { "prefix-$it" }'                                     | "prefix-hello"
        "convention replaced after"       | 'message.convention("hello"); message = message.map { "prefix-$it" }; message.convention("goodbye")'      | "prefix-goodbye"
        "convention installed after"      | 'message = message.map { "prefix-$it" }; message.convention("hello")'                                     | "prefix-hello"
        "no convention at all"            | 'message = message.map { "prefix-$it" }'                                                                  | "missing"
        "set(null) reveals the convention"| 'message.convention("hello"); message = message.map { "prefix-$it" }; message.convention("goodbye"); message = null' | "goodbye"
        "unset releases the chain"        | 'message.convention("hello"); message = message.map { "[$it]" }; message = message.map { "$it!" }; message.unset()' | "hello"
        "orElse falls back to self"       | 'message.convention("Adoptium"); def configured = objects.property(String); message.set(configured.orElse(message))' | "Adoptium"
        "orElse prefers the other source" | 'message.convention("Adoptium"); def configured = objects.property(String); message.set(configured.orElse(message)); configured.set("Oracle")' | "Oracle"
    }

    @Issue("https://github.com/gradle/gradle/issues/18352")
    def "collection self reference appends to the live convention (#description)"() {
        buildFileWithTask(listProperty(), statement)

        when:
        run "myTask"

        then:
        outputContains("Result: $result")

        where:
        description                     | statement                                                                                                           | result
        "convention set before"         | 'args.convention(["-Xmx1g"]); args = args.map { it + "-Dfeature=true" }'                                            | "[-Xmx1g, -Dfeature=true]"
        "convention set after"          | 'args = args.map { it + "-Dfeature=true" }; args.convention(["-Xmx1g"])'                                            | "[-Xmx1g, -Dfeature=true]"
        "convention replaced"           | 'args.convention(["-Xmx1g"]); args = args.map { it + "-Dfeature=true" }; args.convention(["-Xmx2g"])'               | "[-Xmx2g, -Dfeature=true]"
        "remove from the previous value"| 'args.convention(["-Xmx2g"]); args = args.map { it + "-Dfeature=true" }; args = args.map { it - "-Xmx2g" }'         | "[-Dfeature=true]"
        "fresh property is empty"       | 'args = args.map { it + "-Dfeature=true" }'                                                                         | "[-Dfeature=true]"
        "unset reveals the convention"  | 'args.convention(["-Xmx1g"]); args = args.map { it + "-Dfeature=true" }; args.unset()'                              | "[-Xmx1g]"
    }

    def "map self reference transforms the entries"() {
        buildFileWithTask(mapProperty(), """
            entries.convention([k: "old"])
            entries = entries.map { it + [j: "w"] }
        """)

        when:
        run "myTask"

        then:
        outputContains("Result: {k=old, j=w}")
    }

    @Issue("https://github.com/gradle/gradle/issues/8755")
    def "file collection self reference using #description"() {
        file("c.jar").touch()
        file("x.jar").touch()
        file("notes.txt").touch()
        buildFileWithTask(fileCollection(), """
            classpath.setFrom(files("c.jar", "notes.txt"))
            $statement
        """)

        when:
        run "myTask"

        then:
        outputContains("Result: $result")

        where:
        description     | statement                                                      | result
        "plus"          | 'classpath.setFrom(classpath.plus(files("x.jar")))'            | "[c.jar, notes.txt, x.jar]"
        "+="            | 'classpath += files("x.jar")'                                  | "[c.jar, notes.txt, x.jar]"
        "minus"         | 'classpath.setFrom(classpath.minus(files("notes.txt")))'       | "[c.jar]"
        "-="            | 'classpath -= files("notes.txt")'                              | "[c.jar]"
        "filter"        | 'classpath.setFrom(classpath.filter { it.name.endsWith("jar") })' | "[c.jar]"
        "from(self + x)"| 'classpath.from(classpath + files("x.jar"))'                   | "[c.jar, notes.txt, x.jar]"
        "setFrom(self)" | 'classpath.setFrom(classpath)'                                 | "[c.jar, notes.txt]"
    }

    def "file collection self reference reads the convention live"() {
        file("c.jar").touch()
        file("d.jar").touch()
        file("x.jar").touch()
        buildFileWithTask(fileCollection(), """
            classpath.convention(files("c.jar"))
            classpath.setFrom(classpath.plus(files("x.jar")))
            classpath.convention(files("d.jar"))
        """)

        when:
        run "myTask"

        then:
        outputContains("Result: [d.jar, x.jar]")
    }

    def "other properties in a self reference stay live"() {
        buildFileWithTask(stringProperty(), """
            def suffix = objects.property(String)
            message.set("hello")
            suffix.set("!")
            message.set(message.zip(suffix) { previous, ending -> previous + ending })
            suffix.set("?")
        """)

        when:
        run "myTask"

        then:
        outputContains("Result: hello?")
    }

    def "a provider derived from the property stays a live view of the property"() {
        buildFileWithTask(listProperty(), """
            args.set(["-Xmx1g"])
            def extra = args.map { it + "-javaagent:x.jar" }
            args.set(extra)
            doLast {
                println("Extra: " + extra.get())
            }
        """)

        when:
        run "myTask"

        then:
        outputContains("Result: [-Xmx1g, -javaagent:x.jar]")
        outputContains("Extra: [-Xmx1g, -javaagent:x.jar, -javaagent:x.jar]")
    }

    def "self reference with a task output as convention carries the task dependency and is carried through the configuration cache"() {
        buildFile """
            abstract class Producer extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = "produced"
                }
            }

            abstract class Consumer extends DefaultTask {
                @Input
                abstract Property<String> getMessage()

                @TaskAction
                void run() {
                    println("Result: " + message.get())
                }
            }

            def producer = tasks.register("producer", Producer) {
                outputFile = layout.buildDirectory.file("out.txt")
            }

            tasks.register("myTask", Consumer) {
                message.convention(producer.flatMap { it.outputFile }.map { it.asFile.text })
                message.set(message.map { "[" + it + "]" })
            }
        """

        when:
        run "myTask"

        then:
        executed(":producer", ":myTask")
        outputContains("Result: [produced]")

        when:
        run "myTask"

        then:
        outputContains("Result: [produced]")
    }

    def "convention derived from the property itself is rejected"() {
        buildFileWithTask(stringProperty(), """
            message.convention(message.map { "prefix-" + it })
        """)

        when:
        fails "myTask"

        then:
        failureHasCause("Cannot set the convention of task ':myTask' property 'message' using a provider derived from the property itself. Use set() to derive a value from the previous value of the property.")
    }

    def "self reference hidden inside a callback is still a circular evaluation (#description)"() {
        buildFileWithTask(stringProperty(), """
            message.set("hello")
            $statement
        """)

        when:
        fails "myTask"

        then:
        failureCauseContains("Circular evaluation detected")

        where:
        description                   | statement
        "provider { p.get() }"        | 'message.set(provider { message.get() + "!" })'
        "read inside map"             | 'message.set(message.map { previous -> previous + message.get() })'
        "selected by flatMap"         | 'def q = objects.property(Boolean).value(true); message.set(q.flatMap { it ? message : provider { "default" } })'
    }

    def "cross-property cycles and intermediate properties are still circular evaluations (#description)"() {
        buildFileWithTask(stringProperty(), """
            def q = objects.property(String)
            $statement
        """)

        when:
        fails "myTask"

        then:
        failureCauseContains("Circular evaluation detected")

        where:
        description             | statement
        "cross-property cycle"  | 'message.set(q.map { it }); q.set(message.map { it })'
        "intermediate property" | 'message.set("hello"); q.set(message.map { "$it!" }); message.set(q)'
    }
}
