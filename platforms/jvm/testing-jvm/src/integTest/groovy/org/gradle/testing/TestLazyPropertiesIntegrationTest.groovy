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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture

class TestLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testImplementation 'junit:junit:4.13.2' }
            tasks.test {
                testLogging.showStandardStreams = true
            }
        """
        file("src/test/java/EnvTest.java") << """
            import java.lang.management.ManagementFactory;
            public class EnvTest {
                @org.junit.Test
                public void printEnvironment() {
                    System.out.println("jvm args: " + ManagementFactory.getRuntimeMXBean().getInputArguments());
                    System.out.println("property k: " + System.getProperty("k"));
                    System.out.println("env K: " + System.getenv("K"));
                }
            }
        """
    }

    private List<String> jvmArgsOfTestJvm() {
        def line = output.readLines().find { it.contains("jvm args: [") }
        assert line != null
        line.substring(line.indexOf("[") + 1, line.lastIndexOf("]")).split(", ").toList()
    }

    def "jvmArgs are parsed when they are set"() {
        buildFile """
            tasks.test.jvmArgs("-Xmx1g", "-Dk=v", "-XX:+UseSerialGC")
            println("maxHeapSize: " + tasks.test.maxHeapSize)
            println("jvmArgs: " + tasks.test.jvmArgs)
            println("systemProperties: " + tasks.test.systemProperties)
        """

        when:
        succeeds("help")

        then:
        outputContains("maxHeapSize: 1g")
        outputContains("jvmArgs: [-XX:+UseSerialGC]")
        outputContains("systemProperties: [k:v]")
    }

    def "jvmArgs called after maxHeapSize wins, as before"() {
        buildFile """
            tasks.test {
                maxHeapSize = "256m"
                jvmArgs "-Xmx128m"
            }
        """

        when:
        succeeds("test")

        then:
        def args = jvmArgsOfTestJvm()
        args.contains("-Xmx128m")
        !args.contains("-Xmx256m")
    }

    def "jvmArgs bound lazily are parsed when the test JVM is started and win over maxHeapSize"() {
        buildFile """
            tasks.test {
                maxHeapSize = "256m"
                lazyProperties.jvmArgs.add(providers.provider { "-Xmx96m" })
                lazyProperties.jvmArgs.add(providers.provider { "-Dk=lazy" })
            }
        """

        when:
        succeeds("test")

        then:
        def args = jvmArgsOfTestJvm()
        args.contains("-Xmx96m")
        !args.contains("-Xmx256m")
        outputContains("property k: lazy")
    }

    def "system properties and environment set through the live maps reach the test JVM"() {
        buildFile """
            tasks.test {
                systemProperties.put("k", "v")
                environment("K", "V")
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("property k: v")
        outputContains("env K: V")
    }

    def "maxParallelForks below 1 fails in the setter"() {
        buildFile """
            tasks.test.maxParallelForks = 0
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("Cannot set maxParallelForks to a value less than 1.")
    }

    def "maxParallelForks below 1 bound lazily fails at execution"() {
        buildFile """
            tasks.test.lazyProperties.maxParallelForks = providers.provider { 0 }
        """

        when:
        fails("test")

        then:
        failure.assertHasCause("Cannot set maxParallelForks to a value less than 1.")
    }

    def "command line options set their values"() {
        buildFile """
            tasks.test.onlyIf { false }
            gradle.taskGraph.whenReady {
                def test = tasks.test
                println("options: " + [test.failFast, test.debug, test.dryRun.get(), test.filter.commandLineIncludePatterns])
            }
        """

        when:
        succeeds("test", "--fail-fast", "--debug-jvm", "--test-dry-run", "--tests", "EnvTest")

        then:
        outputContains("options: [true, true, true, [EnvTest]]")
    }

    def "maxHeapSize bound to a mapped task output runs that task first and reaches the test JVM"() {
        buildFile """
            abstract class Producer extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = "112m"
                }
            }
            def producer = tasks.register("producer", Producer) {
                outputFile = layout.buildDirectory.file("heap.txt")
            }
            tasks.test {
                lazyProperties.maxHeapSize = producer.flatMap { it.outputFile }.map { it.asFile.text }
            }
        """

        when:
        succeeds("test")

        then:
        result.assertTaskOrder(":producer", ":test")
        jvmArgsOfTestJvm().contains("-Xmx112m")
    }

    def "classpath and testClassesDirs follow the convention mapping and return what was set"() {
        buildFile """
            def test = tasks.test
            println("classpath is runtime classpath: " + test.classpath.is(sourceSets.test.runtimeClasspath))
            println("testClassesDirs is classes dirs: " + test.testClassesDirs.is(sourceSets.test.output.classesDirs))
            def custom = files("custom")
            test.classpath = custom
            println("classpath is set value: " + test.classpath.is(custom))
            tasks.register("plainTest", Test)
            println("plain classpath: " + tasks.plainTest.classpath.files)
        """

        when:
        succeeds("help")

        then:
        outputContains("classpath is runtime classpath: true")
        outputContains("testClassesDirs is classes dirs: true")
        outputContains("classpath is set value: true")
        outputContains("plain classpath: []")
    }

    private static final String PRODUCER = """
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

    private static final String JAVA_EXECUTABLE = "org.gradle.internal.jvm.Jvm.current().javaExecutable.absolutePath"

    def "executable and environment set eagerly are stored in the lazy properties"() {
        buildFile """
            def test = tasks.test
            test.executable = $JAVA_EXECUTABLE
            test.environment("K", "V")
            test.environment(L: "W")
            println("same executable: " + (test.lazyProperties.executable.get() == $JAVA_EXECUTABLE))
            println("environment: " + test.lazyProperties.environment.get().subMap(["K", "L"]))
            def inherited = System.getenv().keySet() - ["K", "L"]
            println("inherited environment kept: " + test.lazyProperties.environment.get().keySet().any { it in inherited })
        """

        when:
        succeeds("help")

        then:
        outputContains("same executable: true")
        outputContains("environment: [K:V, L:W]")
        outputContains("inherited environment kept: true")
    }

    def "executable and environment set lazily are read by the eager getters and reach the test JVM"() {
        buildFile """
            tasks.test {
                lazyProperties.executable = $JAVA_EXECUTABLE
                lazyProperties.environment.put("K", providers.provider { "lazy" })
                println("same executable: " + (executable == $JAVA_EXECUTABLE))
                println("eager environment: " + environment.K)
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("same executable: true")
        outputContains("eager environment: lazy")
        outputContains("env K: lazy")
    }

    def "executable set to null reads null and hides a convention set later, so the toolchain is used"() {
        buildFile """
            tasks.test {
                executable = null
                lazyProperties.executable.convention("does-not-exist")
                println("null: " + executable)
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("null: null")
        outputContains("env K: null")
    }

    def "executable follows the convention mapping, a lazy value wins, and null hides the convention"() {
        buildFile """
            def test = tasks.test
            test.conventionMapping.map("executable") { "mapped" }
            println("mapped: " + test.executable)
            test.lazyProperties.executable = "lazy"
            println("lazy: " + test.executable)
            test.executable = null
            println("null: " + test.executable)
            test.lazyProperties.executable.unset()
            println("unset: " + test.executable)
        """

        when:
        succeeds("help")

        then:
        outputContains("mapped: mapped")
        outputContains("lazy: lazy")
        outputContains("null: null")
        outputContains("unset: mapped")
    }

    def "environment and executable bound lazily survive the configuration cache"() {
        def configurationCache = new ConfigurationCacheFixture(this)
        buildFile """
            tasks.test {
                lazyProperties.executable = providers.provider { $JAVA_EXECUTABLE }
                lazyProperties.environment.put("K", providers.gradleProperty("k"))
            }
        """

        when:
        succeeds("test", "--configuration-cache", "-Pk=first")

        then:
        configurationCache.assertStateStored()
        outputContains("env K: first")

        when:
        file("build/test-results").deleteDir()
        succeeds("test", "--configuration-cache", "-Pk=first")

        then:
        configurationCache.assertStateLoaded()
        outputContains("env K: first")
    }

    def "environment bound to a task output does not run that task, because environment is @Internal"() {
        buildFile """
            $PRODUCER
            def producer = tasks.register("producer", Producer) {
                content = "produced"
                outputFile = layout.buildDirectory.file("env.txt")
            }
            tasks.test {
                lazyProperties.environment.put("K", producer.flatMap { it.outputFile }.map { it.asFile.text })
            }
        """

        when:
        fails("test")

        then:
        result.assertTasksNotScheduled(":producer")
        failureDescriptionStartsWith("Execution failed for task ':test'")
    }

    def "environment bound to a task output works with an explicit dependency"() {
        buildFile """
            $PRODUCER
            def producer = tasks.register("producer", Producer) {
                content = "produced"
                outputFile = layout.buildDirectory.file("env.txt")
            }
            tasks.test {
                dependsOn(producer)
                lazyProperties.environment.put("K", producer.flatMap { it.outputFile }.map { it.asFile.text })
                environment("OTHER", "value")
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("env K: produced")
    }

    def "debug is backed by debugOptions.enabled, and a binding to a task output runs that task first"() {
        buildFile """
            $PRODUCER
            def producer = tasks.register("producer", Producer) {
                content = "false"
                outputFile = layout.buildDirectory.file("debug.txt")
            }
            tasks.test {
                debugOptions.enabled = producer.flatMap { it.outputFile }.map { it.asFile.text.toBoolean() }
            }
        """

        when:
        succeeds("test")

        then:
        result.assertTaskOrder(":producer", ":test")
        outputContains("env K: null")
    }

    def "the lazy properties accessor adds no input property"() {
        buildFile """
            tasks.test {
                doLast {
                    println("inputs: " + inputs.properties.keySet().sort())
                }
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("inputs: [debug, debugOptions, debugOptions.enabled, debugOptions.host, debugOptions.port, debugOptions.server, debugOptions.suspend, defaultCharacterEncoding, dryRun, enableAssertions, failOnNoDiscoveredTests, filter, filter.commandLineIncludePatterns, filter.excludePatterns, filter.failOnNoMatchingTests, filter.includePatterns, javaLauncher, javaLauncher.metadata, javaLauncher.metadata.languageVersion, javaLauncher.metadata.taskInputs, javaLauncher.metadata.taskInputs.implementation, javaLauncher.metadata.taskInputs.languageVersion, javaLauncher.metadata.taskInputs.vendor, javaVersion, jvmArgs, maxHeapSize, minHeapSize, modularity, modularity.inferModulePath, options, options.excludeCategories, options.includeCategories, reports, reports.enabledReports.html, reports.enabledReports.html.displayName, reports.enabledReports.html.name, reports.enabledReports.html.outputType, reports.enabledReports.html.required, reports.enabledReports.junitXml, reports.enabledReports.junitXml.displayName, reports.enabledReports.junitXml.includeSystemErrLog, reports.enabledReports.junitXml.includeSystemOutLog, reports.enabledReports.junitXml.mergeReruns, reports.enabledReports.junitXml.name, reports.enabledReports.junitXml.outputPerTestCase, reports.enabledReports.junitXml.outputType, reports.enabledReports.junitXml.required, scanForTestClasses, systemProperties, testFrameworkProperty, testFrameworkProperty.options, testFrameworkProperty.options.excludeCategories, testFrameworkProperty.options.includeCategories, testLogging]")
    }
}
