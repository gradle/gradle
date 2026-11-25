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
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

class ExecLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

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

            def javaExecutable = org.gradle.internal.jvm.Jvm.current().javaExecutable.absolutePath

            tasks.register("run", Exec) {
                dependsOn("compileJava")
                executable = javaExecutable
                args("-cp", sourceSets.main.output.classesDirs.asPath, "Main")
            }
            tasks.register("probe", Exec)
        """
        file("src/main/java/Main.java") << """
            public class Main {
                public static void main(String[] args) {
                    System.out.println("args: " + java.util.Arrays.asList(args));
                    System.out.println("env K: " + System.getenv("K"));
                    if (args.length > 0 && args[args.length - 1].equals("fail")) {
                        System.exit(3);
                    }
                }
            }
        """
    }

    def "eager values are stored in the lazy properties"() {
        buildFile """
            def probe = tasks.probe
            def exe = new Mutable(value: "first")
            probe.executable(exe)
            probe.args("a", new Mutable(value: "b"))
            probe.environment("K", "V")
            probe.ignoreExitValue = true
            exe.value = "second"
            def lazy = probe.lazyProperties
            println("executable: " + lazy.executable.get())
            println("args: " + lazy.args.get())
            println("env K: " + lazy.environment.get().K)
            println("inherited kept: " + lazy.environment.get().keySet().containsAll(System.getenv().keySet()))
            println("ignoreExitValue: " + lazy.ignoreExitValue.get())
        """

        when:
        succeeds("help")

        then:
        outputContains("executable: second")
        outputContains("args: [a, b]")
        outputContains("env K: V")
        outputContains("inherited kept: true")
        outputContains("ignoreExitValue: true")
    }

    def "eager getters read the lazy properties"() {
        buildFile """
            def probe = tasks.probe
            println("defaults: " + [probe.executable, probe.args, probe.ignoreExitValue, probe.environment == System.getenv()])
            def lazy = probe.lazyProperties
            lazy.executable = "tool"
            lazy.args.add("a")
            lazy.args.add(providers.provider { "b" })
            lazy.environment.put("K", "lazy")
            lazy.ignoreExitValue = true
            println("executable: " + probe.executable)
            println("args: " + probe.args)
            println("commandLine: " + probe.commandLine)
            println("env K: " + probe.environment.K)
            println("inherited kept: " + probe.environment.keySet().containsAll(System.getenv().keySet()))
            println("ignoreExitValue: " + probe.ignoreExitValue)
            lazy.environment.set((Map) null)
            println("env reset: " + (probe.environment == System.getenv()))
        """

        when:
        succeeds("help")

        then:
        outputContains("defaults: [null, [], false, true]")
        outputContains("inherited kept: true")
        outputContains("env reset: true")
        outputContains("executable: tool")
        outputContains("args: [a, b]")
        outputContains("commandLine: [tool, a, b]")
        outputContains("env K: lazy")
        outputContains("ignoreExitValue: true")
    }

    def "commandLine writes the executable and the arguments"() {
        buildFile """
            def probe = tasks.probe
            probe.args("old")
            probe.commandLine("tool", "a", new Mutable(value: "b"))
            println("lazy: " + [probe.lazyProperties.executable.get(), probe.lazyProperties.args.get()])
        """

        when:
        succeeds("help")

        then:
        outputContains("lazy: [tool, [a, b]]")
    }

    def "args, environment and ignoreExitValue set lazily reach the process"() {
        buildFile """
            tasks.run {
                lazyProperties.args.add(providers.provider { "lazy-arg" })
                lazyProperties.args.add("fail")
                lazyProperties.environment.put("K", providers.provider { "lazy" })
                lazyProperties.ignoreExitValue = providers.provider { true }
            }
        """

        when:
        succeeds("run")

        then:
        outputContains("args: [lazy-arg, fail]")
        outputContains("env K: lazy")
    }

    def "eager changes in doFirst reach the process"() {
        buildFile """
            tasks.run {
                doFirst {
                    args("changed")
                    environment("K", "changed")
                }
            }
        """

        when:
        succeeds("run")

        then:
        outputContains("args: [changed]")
        outputContains("env K: changed")
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

    def "executable bound to a task output runs that task first"() {
        buildFile """
            def producer = tasks.register("producer", Producer) {
                content = javaExecutable
                outputFile = layout.buildDirectory.file("executable.txt")
            }
            tasks.run {
                lazyProperties.executable = producer.flatMap { it.outputFile }.map { it.asFile.text }
            }
        """

        when:
        succeeds("run")

        then:
        result.assertTaskOrder(":producer", ":run")
        outputContains("args: []")
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

    def "environment bound to a task output works with an explicit dependency, also when eager entries are added later"() {
        buildFile """
            def producer = tasks.register("producer", Producer) {
                content = "produced"
                outputFile = layout.buildDirectory.file("env.txt")
            }
            tasks.run {
                dependsOn(producer)
                lazyProperties.environment.put("K", producer.flatMap { it.outputFile }.map { it.asFile.text })
                environment("OTHER", "value")
                environment(OTHER2: "value")
            }
        """

        when:
        succeeds("run")

        then:
        outputContains("env K: produced")
    }

    def "executable follows the convention mapping, a lazy value wins, and null hides the convention"() {
        buildFile """
            def probe = tasks.probe
            probe.conventionMapping.map("executable") { "mapped" }
            println("mapped: " + probe.executable)
            probe.lazyProperties.executable = "lazy"
            println("lazy: " + probe.executable)
            probe.executable = null
            println("null: " + probe.executable)
            probe.lazyProperties.executable.unset()
            println("unset: " + probe.executable)
        """

        when:
        succeeds("help")

        then:
        outputContains("mapped: mapped")
        outputContains("lazy: lazy")
        outputContains("null: null")
        outputContains("unset: mapped")
    }

    def "executable from a convention mapping reaches the process"() {
        buildFile """
            tasks.run {
                executable = null
                lazyProperties.executable.unset()
                conventionMapping.map("executable") { javaExecutable }
            }
        """

        when:
        succeeds("run")

        then:
        outputContains("args: []")
    }

    def "executable set to null hides a convention set later"() {
        buildFile """
            def probe = tasks.probe
            probe.executable = null
            probe.lazyProperties.executable.convention("later")
            println("null: " + probe.executable)
            probe.lazyProperties.executable.set(null)
            println("lazy null: " + probe.executable)
        """

        when:
        succeeds("help")

        then:
        outputContains("null: null")
        outputContains("lazy null: later")
    }

    def "environment entry set to null is passed to the process as before"() {
        buildFile """
            tasks.run {
                environment("K", null)
            }
        """

        when:
        succeeds("run")

        then:
        outputContains("env K: null")
    }

    @Requires(value = TestExecutionPreconditions.NotEmbeddedExecutor, reason = "sets environment variables of the build")
    def "inherited environment is read when the task runs, also when the configuration cache is reused"() {
        def configurationCache = new ConfigurationCacheFixture(this)
        buildFile """
            tasks.run {
                if (providers.gradleProperty("changeEnvironment").present) {
                    environment("OTHER", "value")
                }
            }
        """

        when:
        executer.withEnvironmentVars(K: "first")
        succeeds("run", "--configuration-cache", *extraArgs)

        then:
        configurationCache.assertStateStored()
        outputContains("env K: first")

        when:
        executer.withEnvironmentVars(K: "second")
        succeeds("run", "--configuration-cache", *extraArgs)

        then:
        configurationCache.assertStateLoaded()
        outputContains("env K: second")

        where:
        extraArgs << [[], ["-PchangeEnvironment"]]
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
        outputContains("inputs: [args, executable, ignoreExitValue]")
    }
}
