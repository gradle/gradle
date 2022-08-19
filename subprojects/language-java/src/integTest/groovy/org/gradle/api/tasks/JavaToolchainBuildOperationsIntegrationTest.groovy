/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.jvm.JavaToolchainBuildOperationsFixture
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

class JavaToolchainBuildOperationsIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainBuildOperationsFixture {

    static kgpLatestVersions = new KotlinGradlePluginVersions().latests.toList()

    def setup() {
        captureBuildOperations()

        buildFile << """
            apply plugin: "java"

            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'junit:junit:4.13'
            }
        """
    }

    @ToBeImplemented("All cases are supported except up-to-dateness for the javadoc task when toolchains are not configured")
    @Issue("https://github.com/gradle/gradle/issues/21386")
    def "emits toolchain usages for a build #configureToolchain configured toolchain for '#task' task"() {
        JvmInstallationMetadata jdkMetadata
        if (configureToolchain == "without") {
            jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(Jvm.current())
        } else {
            jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)

            if (configureToolchain == "with java plugin") {
                configureToolchainViaJavaPlugin(jdkMetadata)
            } else if (configureToolchain == "with per task") {
                configureToolchainPerTask(jdkMetadata)
            } else if (configureToolchain == "with java plugin and per task") {
                configureToolchainViaJavaPlugin(AvailableJavaHomes.getJvmInstallationMetadata(Jvm.current()))
                configureToolchainPerTask(jdkMetadata)
            } else {
                throw new IllegalArgumentException("Unknown configureToolchain: " + configureToolchain)
            }
        }

        file("src/main/java/Foo.java") << """
            /**
             * This is a {@code Foo} class.
             */
            public class Foo {}
        """

        file("src/test/java/FooTest.java") << """
            public class FooTest {
                @org.junit.Test
                public void test() {}
            }
        """

        when:
        runWithInstallation(jdkMetadata, task)
        def events = toolchainEvents(task)
        then:
        executedAndNotSkipped(task)
        assertToolchainUsages(events, jdkMetadata, tool)

        when:
        runWithInstallation(jdkMetadata, task)
        events = toolchainEvents(task)
        then:
        skipped(task)
        if (emitsWhenUpToDate) {
            assertToolchainUsages(events, jdkMetadata, tool)
        }

        where:
        task           | tool           | configureToolchain              | emitsWhenUpToDate
        ":compileJava" | "JavaCompiler" | "with java plugin"              | true
        ":compileJava" | "JavaCompiler" | "with per task"                 | true
        ":compileJava" | "JavaCompiler" | "with java plugin and per task" | true
        ":compileJava" | "JavaCompiler" | "without"                       | true
        ":test"        | "JavaLauncher" | "with java plugin"              | true
        ":test"        | "JavaLauncher" | "with per task"                 | true
        ":test"        | "JavaLauncher" | "with java plugin and per task" | true
        ":test"        | "JavaLauncher" | "without"                       | true
        ":javadoc"     | "JavadocTool"  | "with java plugin"              | true
        ":javadoc"     | "JavadocTool"  | "with per task"                 | true
        ":javadoc"     | "JavadocTool"  | "with java plugin and per task" | true
        ":javadoc"     | "JavadocTool"  | "without"                       | false
    }

    def "emits toolchain usages for a custom task that uses a toolchain property"() {
        def task = ":myToolchainTask"

        JvmInstallationMetadata jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)

        buildFile << """
            abstract class ToolchainTask extends DefaultTask {
                @Nested
                abstract Property<JavaLauncher> getLauncher1()
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void myAction() {
                    def output = outputFile.get().asFile
                    output << launcher1.get().executablePath
                }
            }

            tasks.register("myToolchainTask", ToolchainTask) {
                outputFile = layout.buildDirectory.file("output.txt")
                launcher1 = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata.languageVersion.majorVersion})
                }
            }
        """

        when:
        runWithInstallation(jdkMetadata, task)
        def events = toolchainEvents(task)
        then:
        executedAndNotSkipped(task)
        assertToolchainUsages(events, jdkMetadata, "JavaLauncher")

        when:
        runWithInstallation(jdkMetadata, task)
        events = toolchainEvents(task)
        then:
        skipped(task)
        assertToolchainUsages(events, jdkMetadata, "JavaLauncher")
    }

    def "emits toolchain usages for a custom task that uses two different toolchains"() {
        def task = ":myToolchainTask"

        JvmInstallationMetadata jdkMetadata1 = AvailableJavaHomes.getJvmInstallationMetadata(Jvm.current())
        JvmInstallationMetadata jdkMetadata2 = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentVersion)

        buildFile << """
            abstract class ToolchainTask extends DefaultTask {
                @Nested
                abstract Property<JavaLauncher> getLauncher1()
                @Nested
                abstract Property<JavaLauncher> getLauncher2()
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void myAction() {
                    def output = outputFile.get().asFile
                    output << launcher1.get().executablePath
                    output << launcher2.get().executablePath
                }
            }

            tasks.register("myToolchainTask", ToolchainTask) {
                outputFile = layout.buildDirectory.file("output.txt")
                launcher1 = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata1.languageVersion.majorVersion})
                }
                launcher2 = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata2.languageVersion.majorVersion})
                }
            }
        """

        def installationPaths = [jdkMetadata1, jdkMetadata2].collect { it.javaHome.toAbsolutePath().toString() }.join(",")

        when:
        runWithInstallationPaths(installationPaths, task)
        def events = toolchainEvents(task)
        def events1 = filterByJavaVersion(events, jdkMetadata1)
        def events2 = filterByJavaVersion(events, jdkMetadata2)
        then:
        executedAndNotSkipped(task)
        events.size() > 0
        events.size() == events1.size() + events2.size() // no events from other toolchains
        assertToolchainUsages(events1, jdkMetadata1, "JavaLauncher")
        assertToolchainUsages(events2, jdkMetadata2, "JavaLauncher")

        when:
        runWithInstallationPaths(installationPaths, task)
        events = toolchainEvents(task)
        events1 = filterByJavaVersion(events, jdkMetadata1)
        events2 = filterByJavaVersion(events, jdkMetadata2)
        then:
        skipped(task)
        events.size() > 0
        events.size() == events1.size() + events2.size() // no events from other toolchains
        assertToolchainUsages(events1, jdkMetadata1, "JavaLauncher")
        assertToolchainUsages(events2, jdkMetadata2, "JavaLauncher")
    }

    def "emits toolchain usages for custom tasks each using a different toolchain"() {
        JvmInstallationMetadata jdkMetadata1 = AvailableJavaHomes.getJvmInstallationMetadata(Jvm.current())
        JvmInstallationMetadata jdkMetadata2 = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentVersion)

        buildFile << """
            abstract class ToolchainTask extends DefaultTask {
                @Nested
                abstract Property<JavaLauncher> getLauncher1()
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void myAction() {
                    def output = outputFile.get().asFile
                    output << launcher1.get().executablePath
                }
            }

            tasks.register("myToolchainTask1", ToolchainTask) {
                outputFile = layout.buildDirectory.file("output1.txt")
                launcher1 = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata1.languageVersion.majorVersion})
                }
            }

            tasks.register("myToolchainTask2", ToolchainTask) {
                outputFile = layout.buildDirectory.file("output2.txt")
                launcher1 = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata2.languageVersion.majorVersion})
                }
            }
        """

        def installationPaths = [jdkMetadata1, jdkMetadata2].collect { it.javaHome.toAbsolutePath().toString() }.join(",")

        when:
        runWithInstallationPaths(installationPaths, ":myToolchainTask1", ":myToolchainTask2")
        def events1 = toolchainEvents(":myToolchainTask1")
        def events2 = toolchainEvents(":myToolchainTask2")
        then:
        executedAndNotSkipped(":myToolchainTask1", ":myToolchainTask2")
        assertToolchainUsages(events1, jdkMetadata1, "JavaLauncher")
        assertToolchainUsages(events2, jdkMetadata2, "JavaLauncher")

        when:
        runWithInstallationPaths(installationPaths, ":myToolchainTask1", ":myToolchainTask2")
        events1 = toolchainEvents(":myToolchainTask1")
        events2 = toolchainEvents(":myToolchainTask2")
        then:
        skipped(":myToolchainTask1", ":myToolchainTask2")
        assertToolchainUsages(events1, jdkMetadata1, "JavaLauncher")
        assertToolchainUsages(events2, jdkMetadata2, "JavaLauncher")
    }

    def "emits toolchain usages for compilation that configures java home via fork options"() {
        JvmInstallationMetadata jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)

        buildFile << """
            compileJava {
                options.forkOptions.javaHome = file("${TextUtil.normaliseFileSeparators(jdkMetadata.javaHome.toString())}")
            }
        """

        file("src/main/java/Foo.java") << """
            public class Foo {}
        """

        def task = ":compileJava"

        when:
        runWithInstallation(jdkMetadata, task)
        def events = toolchainEvents(task)
        then:
        executedAndNotSkipped(task)
        assertToolchainUsages(events, jdkMetadata, "JavaCompiler")

        when:
        runWithInstallation(jdkMetadata, task)
        events = toolchainEvents(task)
        then:
        skipped(task)
        assertToolchainUsages(events, jdkMetadata, "JavaCompiler")
    }

    def "emits toolchain usages for compilation that configures java home via fork options pointing outside installations"() {
        JvmInstallationMetadata jdkMetadata1 = AvailableJavaHomes.getJvmInstallationMetadata(Jvm.current())
        JvmInstallationMetadata jdkMetadata2 = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentVersion)

        buildFile << """
            compileJava {
                options.forkOptions.javaHome = file("${TextUtil.normaliseFileSeparators(jdkMetadata2.javaHome.toString())}")
            }
        """

        file("src/main/java/Foo.java") << """
            public class Foo {}
        """

        def task = ":compileJava"

        when:
        runWithInstallation(jdkMetadata1, task)
        def events = toolchainEvents(task)
        then:
        executedAndNotSkipped(task)
        assertToolchainUsages(events, jdkMetadata2, "JavaCompiler")

        when:
        runWithInstallation(jdkMetadata1, task)
        events = toolchainEvents(task)
        then:
        skipped(task)
        assertToolchainUsages(events, jdkMetadata2, "JavaCompiler")
    }

    @ToBeImplemented("finding a used toolchain by the path of the launcher executable")
    @Issue("https://github.com/gradle/gradle/issues/21367")
    def "emits toolchain usages for test that configures executable path"() {
        JvmInstallationMetadata jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentVersion)

        buildFile << """
            compileTestJava {
                javaCompiler = javaToolchains.compilerFor {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata.languageVersion.majorVersion})
                }
            }

            def javaExecutable = javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(${jdkMetadata.languageVersion.majorVersion})
            }.get().executablePath

            test {
                executable = javaExecutable
            }
        """

        file("src/test/java/FooTest.java") << """
            public class FooTest {
                @org.junit.Test
                public void test() {}
            }
        """

        def task = ":test"

        when:
        runWithInstallation(jdkMetadata, task)
        def events = toolchainEvents(task)
        then:
        executedAndNotSkipped(task)
        // TODO: replace when fixed
        events.size() == 0
//        assertToolchainUsages(events, jdkMetadata, "JavaLauncher")

        when:
        runWithInstallation(jdkMetadata, task)
        events = toolchainEvents(task)
        then:
        skipped(task)
        // TODO: replace when fixed
        events.size() == 0
//        assertToolchainUsages(events, jdkMetadata, "JavaLauncher")
    }

    @ToBeFixedForConfigurationCache(because = "Kotlin plugin extracts metadata from the JavaLauncher and wraps it into a custom property")
    @Issue("https://github.com/gradle/gradle/issues/21368")
    def "emits toolchain usages when configuring toolchains for Kotlin plugin '#kotlinPlugin'"() {
        JvmInstallationMetadata jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentVersion)

        def kotlinPluginVersion = kotlinPlugin == "latest" ? kgpLatestVersions.last() : latestKotlinPluginVersion(kotlinPlugin)

        // override setup
        buildFile.text = """
            buildscript {
                ${mavenCentralRepository()}
                dependencies { classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinPluginVersion" }
            }

            apply plugin: "kotlin"

            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'junit:junit:4.13'
            }

            kotlin {
                jvmToolchain {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata.languageVersion.majorVersion})
                }
            }
        """

        file("src/main/kotlin/Foo.kt") << """
            class Foo {
                fun random() = 4
            }
        """

        file("src/test/kotlin/FooTest.kt") << """
            class FooTest {
                @org.junit.Test
                fun test() {}
            }
        """

        when:
        runWithInstallation(jdkMetadata, ":compileKotlin", ":test")
        def eventsOnCompile = toolchainEvents(":compileKotlin")
        def eventsOnTest = toolchainEvents(":test")
        then:
        executedAndNotSkipped(":compileKotlin", ":test")
        // The tool is a launcher, because kotlin runs own compilation in a Java VM
        assertToolchainUsages(eventsOnCompile, jdkMetadata, "JavaLauncher")
        // Even though we only configure the toolchain within the `kotlin` block,
        // it actually affects the java launcher selected by the test task.
        assertToolchainUsages(eventsOnTest, jdkMetadata, "JavaLauncher")

        when:
        runWithInstallation(jdkMetadata, ":compileKotlin", ":test")
        eventsOnCompile = toolchainEvents(":compileKotlin")
        eventsOnTest = toolchainEvents(":test")
        then:
        skipped(":compileKotlin", ":test")
        assertToolchainUsages(eventsOnCompile, jdkMetadata, "JavaLauncher")
        assertToolchainUsages(eventsOnTest, jdkMetadata, "JavaLauncher")

        where:
        kotlinPlugin | _
        "latest"     | _
        "1.7"        | _
        "1.6"        | _
    }

    def "emits toolchain usages when task fails for 'compileJava' task"() {
        def task = ":compileJava"

        JvmInstallationMetadata jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)

        configureToolchainViaJavaPlugin(jdkMetadata)

        file("src/main/java/Foo.java") << """
            public class Foo extends Oops {}
        """

        when:
        runWithInstallationExpectingFailure(jdkMetadata, task)
        def events = toolchainEvents(task)
        then:
        failureDescriptionStartsWith("Execution failed for task '${task}'.")
        failureHasCause("Compilation failed; see the compiler error output for details.")
        result.assertHasErrorOutput("Foo.java:2: error: cannot find symbol")
        assertToolchainUsages(events, jdkMetadata, "JavaCompiler")
    }

    def "emits toolchain usages when task fails for 'test' task"() {
        def task = ":test"

        JvmInstallationMetadata jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)

        configureToolchainViaJavaPlugin(jdkMetadata)

        file("src/test/java/FooTest.java") << """
            public class FooTest {
                @org.junit.Test
                public void test() { org.junit.Assert.assertEquals(1, 2); }
            }
        """

        when:
        runWithInstallationExpectingFailure(jdkMetadata, task)
        def events = toolchainEvents(task)
        then:
        failureDescriptionStartsWith("Execution failed for task '${task}'.")
        failureHasCause("There were failing tests.")
        assertToolchainUsages(events, jdkMetadata, "JavaLauncher")
    }

    def "emits toolchain usages when task fails for 'javadoc' task"() {
        def task = ":javadoc"

        JvmInstallationMetadata jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)

        configureToolchainViaJavaPlugin(jdkMetadata)

        file("src/main/java/Foo.java") << """
            /**
             * This is a {@link Oops} class.
             */
            public class Foo {}
        """

        when:
        runWithInstallationExpectingFailure(jdkMetadata, task)
        def events = toolchainEvents(task)
        then:
        failureDescriptionStartsWith("Execution failed for task '${task}'.")
        failureCauseContains("Javadoc generation failed")
        assertToolchainUsages(events, jdkMetadata, "JavadocTool")
    }

    def "ignores toolchain usages at configuration time"() {
        JvmInstallationMetadata jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)
        buildFile << """
            println(javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(${jdkMetadata.languageVersion.majorVersion})
            }.get().executablePath)

            task myTask {
                doLast {
                    println "Hello from \${name}"
                }
            }
        """

        when:
        runWithInstallation(jdkMetadata, ":myTask")
        def events = toolchainEvents(":myTask")

        then:
        events.size() == 0
        output.contains(jdkMetadata.javaHome.toString())
    }

    private TestFile configureToolchainPerTask(JvmInstallationMetadata jdkMetadata) {
        buildFile << """
            compileJava {
                javaCompiler = javaToolchains.compilerFor {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata.languageVersion.majorVersion})
                }
            }

            compileTestJava {
                javaCompiler = javaToolchains.compilerFor {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata.languageVersion.majorVersion})
                }
            }

            test {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata.languageVersion.majorVersion})
                }
            }

            javadoc {
                javadocTool = javaToolchains.javadocToolFor {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata.languageVersion.majorVersion})
                }
            }
        """
    }

    private TestFile configureToolchainViaJavaPlugin(JvmInstallationMetadata jdkMetadata) {
        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata.languageVersion.majorVersion})
                }
            }
        """
    }

    def runWithInstallation(JvmInstallationMetadata jdkMetadata, String... tasks) {
        runWithInstallationPaths(jdkMetadata.javaHome.toAbsolutePath().toString(), tasks)
    }

    def runWithInstallationPaths(String installationPaths, String... tasks) {
        result = prepareRunWithInstallationPaths(installationPaths, tasks)
            .run()
    }

    def runWithInstallationExpectingFailure(JvmInstallationMetadata jdkMetadata, String... tasks) {
        failure = prepareRunWithInstallationPaths(jdkMetadata.javaHome.toAbsolutePath().toString(), tasks)
            .runWithFailure()
    }

    def prepareRunWithInstallationPaths(String installationPaths, String... tasks) {
        executer
            .withArgument("-Porg.gradle.java.installations.paths=" + installationPaths)
            .withTasks(tasks)
    }

    private String latestKotlinPluginVersion(String major) {
        return kgpLatestVersions.findAll { it.startsWith(major) }.last()
    }
}
