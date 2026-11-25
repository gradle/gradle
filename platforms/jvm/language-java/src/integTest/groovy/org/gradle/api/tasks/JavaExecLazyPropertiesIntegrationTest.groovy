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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaExecLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile """
            plugins {
                id("java")
            }

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

            class Mutable {
                String value
                String toString() { value }
            }

            tasks.register("run", JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = "Main"
            }
        """
        file("src/main/java/Main.java") << """
            public class Main {
                public static void main(String[] args) {
                    System.out.println("args: " + java.util.Arrays.asList(args));
                    System.out.println("env K: " + System.getenv("K"));
                    System.out.println("property k: " + System.getProperty("k"));
                    System.out.println("jvm args: " + java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
                    if (args.length > 0 && args[args.length - 1].equals("fail")) {
                        System.exit(3);
                    }
                }
            }
        """
    }

    private List<String> jvmArgsOfProcess() {
        def line = output.readLines().find { it.startsWith("jvm args: [") }
        assert line != null
        line.substring(line.indexOf("[") + 1, line.lastIndexOf("]")).split(", ").toList()
    }

    def "eager values are stored in the lazy properties"() {
        buildFile """
            def run = tasks.run
            run.minHeapSize = "16m"
            run.maxHeapSize = "96m"
            run.systemProperty("k", new Mutable(value: "v"))
            run.enableAssertions = true
            run.defaultCharacterEncoding = "UTF-16"
            run.bootstrapClasspath(files("boot.jar"))
            run.args("a", new Mutable(value: "b"))
            run.environment("K", "V")
            run.ignoreExitValue = true
            run.executable("custom-java")
            def lazy = run.lazyProperties
            println("heap: " + [lazy.minHeapSize.get(), lazy.maxHeapSize.get()])
            println("systemProperties: " + lazy.systemProperties.get())
            println("assertions and encoding: " + [lazy.enableAssertions.get(), lazy.defaultCharacterEncoding.get()])
            println("bootstrapClasspath: " + lazy.bootstrapClasspath.files*.name)
            println("args: " + lazy.args.get())
            println("env K: " + lazy.environment.get().K)
            println("ignoreExitValue: " + lazy.ignoreExitValue.get())
            println("executable: " + lazy.executable.get())
        """

        when:
        succeeds("help")

        then:
        outputContains("heap: [16m, 96m]")
        outputContains("systemProperties: [k:v]")
        outputContains("assertions and encoding: [true, UTF-16]")
        outputContains("bootstrapClasspath: [boot.jar]")
        outputContains("args: [a, b]")
        outputContains("env K: V")
        outputContains("ignoreExitValue: true")
        outputContains("executable: custom-java")
    }

    def "values set lazily are read by the eager getters and reach the process"() {
        buildFile """
            tasks.run {
                lazyProperties.maxHeapSize = providers.provider { "80m" }
                lazyProperties.systemProperties.put("k", "lazy")
                lazyProperties.args.add(providers.provider { "lazy-arg" })
                lazyProperties.args.add("fail")
                lazyProperties.environment.put("K", "lazy")
                lazyProperties.ignoreExitValue = true
                lazyProperties.enableAssertions = false
                println("eager: " + [maxHeapSize, systemProperties, args, environment.K, ignoreExitValue, enableAssertions])
                println("allJvmArgs has heap: " + allJvmArgs.contains("-Xmx80m"))
            }
        """

        when:
        succeeds("run")

        then:
        outputContains("eager: [80m, [k:lazy], [lazy-arg, fail], lazy, true, false]")
        outputContains("allJvmArgs has heap: true")
        outputContains("args: [lazy-arg, fail]")
        outputContains("env K: lazy")
        outputContains("property k: lazy")
        jvmArgsOfProcess().contains("-Xmx80m")
    }

    def "eager changes in doFirst reach the process"() {
        buildFile """
            tasks.run {
                doFirst {
                    args("changed")
                    environment("K", "changed")
                    systemProperty("k", "changed")
                    maxHeapSize = "88m"
                }
            }
        """

        when:
        succeeds("run")

        then:
        outputContains("args: [changed]")
        outputContains("env K: changed")
        outputContains("property k: changed")
        jvmArgsOfProcess().contains("-Xmx88m")
    }

    def "jvmArgs are stored in jvmArguments, and a binding to a task output runs that task first"() {
        buildFile """
            def producer = tasks.register("producer", Producer) {
                content = "-Dk=produced"
                outputFile = layout.buildDirectory.file("jvm-arg.txt")
            }
            tasks.run {
                jvmArgs("-Dother=eager")
                println("jvmArguments: " + jvmArguments.get())
                jvmArguments.add(producer.flatMap { it.outputFile }.map { it.asFile.text })
                doFirst {
                    println("jvmArgs: " + jvmArgs)
                }
            }
        """

        when:
        succeeds("run")

        then:
        result.assertTaskOrder(":producer", ":run")
        outputContains("jvmArguments: [-Dother=eager]")
        outputContains("jvmArgs: [-Dother=eager, -Dk=produced]")
        outputContains("property k: produced")
    }

    def "maxHeapSize bound to a task output runs that task first"() {
        buildFile """
            def producer = tasks.register("producer", Producer) {
                content = "72m"
                outputFile = layout.buildDirectory.file("heap.txt")
            }
            tasks.run {
                lazyProperties.maxHeapSize = producer.flatMap { it.outputFile }.map { it.asFile.text }
            }
        """

        when:
        succeeds("run")

        then:
        result.assertTaskOrder(":producer", ":run")
        jvmArgsOfProcess().contains("-Xmx72m")
    }

    def "args bound to a task output run that task first"() {
        buildFile """
            def producer = tasks.register("producer", Producer) {
                content = "produced"
                outputFile = layout.buildDirectory.file("arg.txt")
            }
            tasks.run {
                lazyProperties.args.add(producer.flatMap { it.outputFile }.map { it.asFile.text })
            }
        """

        when:
        succeeds("run")

        then:
        result.assertTaskOrder(":producer", ":run")
        outputContains("args: [produced]")
    }

    def "environment bound to a task output does not run that task, because environment is @Internal"() {
        buildFile """
            def producer = tasks.register("producer", Producer) {
                content = "produced"
                outputFile = layout.buildDirectory.file("env.txt")
            }
            tasks.run {
                lazyProperties.environment.put("K", producer.flatMap { it.outputFile }.map { it.asFile.text })
            }
        """

        when:
        fails("run")

        then:
        result.assertTasksNotScheduled(":producer")
        failureDescriptionStartsWith("Execution failed for task ':run'")
    }

    def "maxHeapSize and jvmArgs follow the convention mapping, and null hides the convention"() {
        buildFile """
            tasks.run {
                conventionMapping.map("maxHeapSize") { "64m" }
                conventionMapping.map("jvmArgs") { ["-Dk=mapped"] }
                println("mapped: " + [maxHeapSize, jvmArgs])
                maxHeapSize = null
                println("null: " + maxHeapSize)
                lazyProperties.maxHeapSize.unset()
                println("unset: " + maxHeapSize)
            }
        """

        when:
        succeeds("run")

        then:
        outputContains("mapped: [64m, [-Dk=mapped]]")
        outputContains("null: null")
        outputContains("unset: 64m")
        outputContains("property k: mapped")
        jvmArgsOfProcess().contains("-Xmx64m")
    }

    def "maxHeapSize set to null hides a convention set later"() {
        buildFile """
            def run = tasks.run
            run.maxHeapSize = null
            run.lazyProperties.maxHeapSize.convention("48m")
            println("null: " + run.maxHeapSize)
            run.lazyProperties.maxHeapSize.set((String) null)
            println("lazy null: " + run.maxHeapSize)
        """

        when:
        succeeds("help")

        then:
        outputContains("null: null")
        outputContains("lazy null: 48m")
    }

    def "classpath getter returns the lazy collection, which can refer to itself"() {
        buildFile """
            def run = tasks.run
            def before = run.classpath
            println("is lazy collection: " + before.is(run.lazyProperties.classpath))
            run.classpath = run.classpath + files("extra.jar")
            println("same object after set: " + before.is(run.classpath))
            println("files: " + run.classpath.files*.name.contains("extra.jar"))
            run.classpath = run.classpath - files("extra.jar")
            println("reduced: " + run.classpath.files*.name.contains("extra.jar"))
            // As Spring Boot's BootRun.sourceResources() does
            run.classpath = project.files("resources", run.classpath).filter { it.name != "excluded.jar" }
            println("filtered: " + run.classpath.files*.name.contains("resources"))
        """

        when:
        succeeds("help")

        then:
        outputContains("is lazy collection: true")
        outputContains("same object after set: true")
        outputContains("files: true")
        outputContains("reduced: false")
        outputContains("filtered: true")
    }

    def "classpath bound lazily to a task output runs that task first"() {
        buildFile """
            tasks.run {
                classpath = files()
                lazyProperties.classpath.from(tasks.named("compileJava"))
            }
        """

        when:
        succeeds("run")

        then:
        result.assertTaskOrder(":compileJava", ":run")
        outputContains("args: []")
    }

    def "command line options set their values"() {
        buildFile """
            tasks.run.onlyIf { false }
            gradle.taskGraph.whenReady {
                def run = tasks.run
                println("options: " + [run.args, run.debug, run.debugOptions.enabled.get()])
            }
        """

        when:
        succeeds("run", "--args", "x y", "--debug-jvm")

        then:
        outputContains("options: [[x, y], true, true]")
    }

    def "subclass that changes the environment and jvmArgs when it runs, as Spring Boot's BootRun does"() {
        buildFile """
            abstract class CustomRun extends JavaExec {
                @Override
                void exec() {
                    environment.put("K", true)
                    jvmArgs("-Dk=subclass")
                    setArgs(getArgs() + ["from-subclass"])
                    super.exec()
                }
            }
            tasks.register("customRun", CustomRun) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = "Main"
                args("configured")
            }
        """

        when:
        succeeds("customRun")

        then:
        outputContains("args: [configured, from-subclass]")
        outputContains("env K: true")
        outputContains("property k: subclass")
    }

    def "the lazy properties accessor adds no input property"() {
        buildFile """
            tasks.run {
                doLast {
                    println("inputs: " + inputs.properties.keySet().sort())
                }
            }
        """

        when:
        succeeds("run")

        then:
        outputContains("inputs: [args, debug, debugOptions, debugOptions.enabled, debugOptions.host, debugOptions.port, debugOptions.server, debugOptions.suspend, defaultCharacterEncoding, enableAssertions, ignoreExitValue, javaLauncher, javaLauncher.metadata, javaLauncher.metadata.languageVersion, javaLauncher.metadata.taskInputs, javaLauncher.metadata.taskInputs.implementation, javaLauncher.metadata.taskInputs.languageVersion, javaLauncher.metadata.taskInputs.vendor, jvmArgs, mainClass, mainModule, maxHeapSize, minHeapSize, modularity, modularity.inferModulePath, systemProperties]")
    }
}
