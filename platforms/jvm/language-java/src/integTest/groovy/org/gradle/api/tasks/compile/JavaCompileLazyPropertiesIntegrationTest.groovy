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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaCompileLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile """
            apply plugin: 'java'

            ${mavenCentralRepository()}

            abstract class Producer extends DefaultTask {
                @Input
                abstract Property<String> getContent()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = content.get()
                }
            }
        """
        file("src/main/java/Main.java") << "public class Main {}"
        file("src/other/Other.java") << "public class Other {}"
        file("src/extra/Extra.java") << "public class Extra {}"
    }

    def "classpath can be extended and reduced through the eager getter and setter"() {
        buildFile """
            def task = tasks.compileJava
            task.classpath = task.classpath + files("extra.jar")
            println("plus: " + task.classpath.files.collect { it.name }.sort())
            task.classpath = task.classpath - files("extra.jar")
            println("minus: " + task.classpath.files.collect { it.name }.sort())
        """

        when:
        succeeds("help")

        then:
        outputContains("plus: [extra.jar]")
        outputContains("minus: []")
    }

    def "classpath getter returns the configuration that was set"() {
        buildFile """
            configurations { custom }
            def task = tasks.compileJava
            println("mapped is compileClasspath: " + task.classpath.is(configurations.compileClasspath))
            task.classpath = configurations.custom
            println("is configuration: " + task.classpath.is(configurations.custom))
            println("name: " + task.classpath.name)
        """

        when:
        succeeds("help")

        then:
        outputContains("mapped is compileClasspath: true")
        outputContains("is configuration: true")
        outputContains("name: custom")
    }

    def "source can be changed through the eager getter and setter: #description"() {
        buildFile """
            def task = tasks.compileJava
            $configuration
            println("source: " + task.source.files.collect { it.name }.sort())
        """

        when:
        succeeds("help")

        then:
        outputContains("source: $expected")

        where:
        description                     | configuration                                                                 | expected
        "fileTree after source(..)"     | 'task.source("src/other"); task.source = fileTree("src/other")'              | "[Other.java]"
        "includes still apply"          | 'task.source("src/other"); task.include("Other.java")'                       | "[Other.java]"
    }

    def "source can refer to itself through the eager getter and setter: #description"() {
        buildFile """
            def task = tasks.compileJava
            $configuration
            println("source: " + task.source.files.collect { it.name }.sort())
            println("declared sources: " + task.lazyProperties.source.from.size())
        """

        when:
        succeeds("help")

        then:
        outputContains("source: $expected")
        outputContains("declared sources: 1")

        where:
        description                | configuration                                                                             | expected
        "source + files"           | 'task.source = task.source + files("src/other/Other.java")'                               | "[Main.java, Other.java]"
        "source += files"          | 'task.source += files("src/other/Other.java")'                                            | "[Main.java, Other.java]"
        "setSource(source.filter)" | 'task.source("src/other"); task.setSource(task.source.filter { it.name != "Main.java" })' | "[Other.java]"
    }

    def "source can refer to itself when the getter reads the lazy collection: #description"() {
        buildFile """
            def task = tasks.compileJava
            task.lazyProperties.source.from("src/other")
            $configuration
            println("source: " + task.source.files.collect { it.name }.sort())
        """

        when:
        succeeds("help")

        then:
        outputContains("source: $expected")

        where:
        description                | configuration                                                                       | expected
        "source + files"           | 'task.source = task.source + files("src/extra/Extra.java")'                         | "[Extra.java, Main.java, Other.java]"
        "setSource(source.filter)" | 'task.setSource(task.source.filter { it.name != "Main.java" })'                     | "[Other.java]"
        "source(..) after setFrom" | 'task.lazyProperties.source.setFrom("src/other"); task.source("src/extra")'         | "[Extra.java, Other.java]"
    }

    def "source is not duplicated when it is read at execution time"() {
        buildFile """
            tasks.compileJava {
                source("src/other")
                doLast {
                    println("source at execution: " + source.files.collect { it.name }.sort())
                }
            }
        """

        when:
        succeeds("compileJava")

        then:
        outputContains("source at execution: [Main.java, Other.java]")
    }

    def "compilerArgs can be read, changed and set back"() {
        buildFile """
            def options = tasks.compileJava.options
            def args = options.getCompilerArgs()
            args.add("-parameters")
            options.setCompilerArgs(args)
            println("args: " + options.compilerArgs)
        """

        when:
        succeeds("help")

        then:
        outputContains("args: [-parameters]")
    }

    def "compilerArgs changed in doFirst are used"() {
        buildFile """
            tasks.compileJava {
                doFirst {
                    options.compilerArgs << "-Xlint:all"
                }
                doLast {
                    println("args at execution: " + options.allCompilerArgs)
                }
            }
        """

        when:
        succeeds("compileJava")

        then:
        outputContains("args at execution: [-Xlint:all]")
    }

    def "compilerArgs with a non-String element: #description"() {
        buildFile """
            def options = tasks.compileJava.options
            try {
                options.compilerArgs << $element
                println("result: " + options.allCompilerArgs)
            } catch (Exception e) {
                println("failure: " + e.message)
            }
        """

        when:
        succeeds("help")

        then:
        outputContains(expected)

        where:
        description | element                    | expected
        "GString"   | '"-A${1}"'                 | "result: [-A1]"
        "Integer"   | '42'                       | "result: [42]"
        "File"      | 'file("lib")'              | "lib]"
    }

    def "compiles with a non-String compilerArgs element: #description"() {
        buildFile """
            def max = 5
            tasks.compileJava {
                options.compilerArgs = $args
                doLast {
                    println("args at execution: " + options.allCompilerArgs)
                }
            }
        """

        when:
        succeeds("compileJava")

        then:
        outputContains("args at execution: [-Xmaxerrs, 5]")
        file("build/classes/java/main/Main.class").exists()

        when:
        succeeds("compileJava")

        then:
        result.assertTaskSkipped(":compileJava")

        where:
        description | args
        "Integer"   | '["-Xmaxerrs", 5]'
        "GString"   | '["-Xmaxerrs", "${max}"]'
    }

    def "compilerArgs set from a list stay an alias of that list"() {
        buildFile """
            def options = tasks.compileJava.options
            def args = []
            options.compilerArgs = args
            args << "-parameters"
            println("args: " + options.compilerArgs)
        """

        when:
        succeeds("help")

        then:
        outputContains("args: [-parameters]")
    }

    def "lazy encoding bound to a mapped task output runs that task first"() {
        buildFile """
            def producer = tasks.register("producer", Producer) {
                content = "UTF-8"
                outputFile = layout.buildDirectory.file("encoding.txt")
            }
            tasks.compileJava {
                options.lazyProperties.encoding = producer.flatMap { it.outputFile }.map { it.asFile.text }
                doLast {
                    println("encoding: " + options.encoding)
                }
            }
        """

        when:
        succeeds("compileJava")

        then:
        result.assertTaskOrder(":producer", ":compileJava")
        outputContains("encoding: UTF-8")
    }

    def "encoding set to null hides a convention set before and after, and unset brings it back"() {
        buildFile """
            def options = tasks.compileJava.options
            options.lazyProperties.encoding.convention("UTF-8")
            println("convention: " + options.encoding)
            options.encoding = null
            println("null: " + options.encoding)
            options.lazyProperties.encoding.convention("ISO-8859-1")
            println("later convention: " + options.encoding)
            options.lazyProperties.encoding.unset()
            println("unset: " + options.encoding)
        """

        when:
        succeeds("help")

        then:
        outputContains("convention: UTF-8")
        outputContains("null: null")
        outputContains("later convention: null")
        outputContains("unset: ISO-8859-1")
    }

    def "sourcepath distinguishes never set from an explicitly empty collection"() {
        buildFile """
            def options = tasks.compileJava.options
            println("never set: " + options.sourcepath)
            options.sourcepath = files()
            println("empty: " + (options.sourcepath == null ? "null" : options.sourcepath.files))
            options.sourcepath = null
            println("reset: " + options.sourcepath)
        """

        when:
        succeeds("help")

        then:
        outputContains("never set: null")
        outputContains("empty: []")
        outputContains("reset: null")
    }

    def "source bound to a task output runs that task first"() {
        buildFile """
            def generator = tasks.register("generator", Producer) {
                content = "public class Generated {}"
                outputFile = layout.buildDirectory.file("generated/Generated.java")
            }
            tasks.compileJava {
                source(generator.flatMap { it.outputFile })
            }
        """

        when:
        succeeds("compileJava")

        then:
        result.assertTaskOrder(":generator", ":compileJava")
        file("build/classes/java/main/Generated.class").exists()
    }

    def "sourceCompatibility follows the convention mapping, an explicit value wins, and null hides the convention"() {
        buildFile """
            java { sourceCompatibility = JavaVersion.VERSION_11 }
            def task = tasks.compileJava
            println("convention: " + task.sourceCompatibility)
            println("target convention: " + task.targetCompatibility)
            task.sourceCompatibility = "17"
            println("explicit: " + task.sourceCompatibility)
            task.sourceCompatibility = null
            println("null: " + task.sourceCompatibility)
            task.lazyProperties.sourceCompatibility.unset()
            println("unset: " + task.sourceCompatibility)
        """

        when:
        succeeds("help")

        then:
        outputContains("convention: 11")
        outputContains("target convention: 11")
        outputContains("explicit: 17")
        outputContains("null: null")
        outputContains("unset: 11")
    }

    def "lazy sourceCompatibility bound to a mapped task output runs that task first"() {
        buildFile """
            def producer = tasks.register("producer", Producer) {
                content = "17"
                outputFile = layout.buildDirectory.file("source-compatibility.txt")
            }
            tasks.compileJava {
                lazyProperties.sourceCompatibility = producer.flatMap { it.outputFile }.map { it.asFile.text }
                // The compileClasspath attributes read targetCompatibility while the task graph is built
                targetCompatibility = "17"
                doLast {
                    println("sourceCompatibility: " + sourceCompatibility)
                }
            }
        """

        when:
        succeeds("compileJava")

        then:
        result.assertTaskOrder(":producer", ":compileJava")
        outputContains("sourceCompatibility: 17")
    }

    def "the lazy properties accessor adds no input property"() {
        buildFile """
            tasks.compileJava {
                doLast {
                    println("inputs: " + inputs.properties.keySet().findAll { it.contains("lazy") })
                }
            }
        """

        when:
        succeeds("compileJava")

        then:
        outputContains("inputs: []")
    }
}
