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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.jvm.JavaToolchainBuildOperationsFixture
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil
import org.gradle.util.internal.VersionNumber
import spock.lang.Issue

class JavaToolchainBuildOperationsIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture, JavaToolchainBuildOperationsFixture {

    static kgpLatestVersions = new KotlinGradlePluginVersions().latests

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

    @Issue("https://github.com/gradle/gradle/issues/21386")
    def "emits toolchain usages for a build #configureToolchain configured toolchain for '#task' task"() {
        JvmInstallationMetadata jdkMetadata
        if (configureToolchain == "without") {
            jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(Jvm.current())
        } else {
            jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)

            if (configureToolchain == "with java plugin") {
                configureJavaPluginToolchainVersion(jdkMetadata)
            } else if (configureToolchain == "with per task") {
                configureToolchainPerTask(jdkMetadata)
            } else if (configureToolchain == "with java plugin and per task") {
                configureJavaPluginToolchainVersion(AvailableJavaHomes.getJvmInstallationMetadata(Jvm.current()))
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
        withInstallations(jdkMetadata).run(task)
        def events = toolchainEvents(task)
        then:
        executedAndNotSkipped(task)
        assertToolchainUsages(events, jdkMetadata, tool)

        when:
        withInstallations(jdkMetadata).run(task)
        events = toolchainEvents(task)
        then:
        skipped(task)
        assertToolchainUsages(events, jdkMetadata, tool)

        where:
        task           | tool           | configureToolchain
        ":compileJava" | "JavaCompiler" | "with java plugin"
        ":compileJava" | "JavaCompiler" | "with per task"
        ":compileJava" | "JavaCompiler" | "with java plugin and per task"
        ":compileJava" | "JavaCompiler" | "without"
        ":test"        | "JavaLauncher" | "with java plugin"
        ":test"        | "JavaLauncher" | "with per task"
        ":test"        | "JavaLauncher" | "with java plugin and per task"
        ":test"        | "JavaLauncher" | "without"
        ":javadoc"     | "JavadocTool"  | "with java plugin"
        ":javadoc"     | "JavadocTool"  | "with per task"
        ":javadoc"     | "JavadocTool"  | "with java plugin and per task"
        ":javadoc"     | "JavadocTool"  | "without"
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
        withInstallations(jdkMetadata).run(task)
        def events = toolchainEvents(task)
        then:
        executedAndNotSkipped(task)
        assertToolchainUsages(events, jdkMetadata, "JavaLauncher")

        when:
        withInstallations(jdkMetadata).run(task)
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

        when:
        withInstallations(jdkMetadata1, jdkMetadata2).run(task)
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
        withInstallations(jdkMetadata1, jdkMetadata2).run(task)
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

        when:
        withInstallations(jdkMetadata1, jdkMetadata2).run(":myToolchainTask1", ":myToolchainTask2")
        def events1 = toolchainEvents(":myToolchainTask1")
        def events2 = toolchainEvents(":myToolchainTask2")
        then:
        executedAndNotSkipped(":myToolchainTask1", ":myToolchainTask2")
        assertToolchainUsages(events1, jdkMetadata1, "JavaLauncher")
        assertToolchainUsages(events2, jdkMetadata2, "JavaLauncher")

        when:
        withInstallations(jdkMetadata1, jdkMetadata2).run(":myToolchainTask1", ":myToolchainTask2")
        events1 = toolchainEvents(":myToolchainTask1")
        events2 = toolchainEvents(":myToolchainTask2")
        then:
        skipped(":myToolchainTask1", ":myToolchainTask2")
        assertToolchainUsages(events1, jdkMetadata1, "JavaLauncher")
        assertToolchainUsages(events2, jdkMetadata2, "JavaLauncher")
    }

    def "emits toolchain usages for compilation that configures #option via fork options"() {
        JvmInstallationMetadata curJdk = AvailableJavaHomes.getJvmInstallationMetadata(Jvm.current())
        JvmInstallationMetadata jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)
        def path = TextUtil.normaliseFileSeparators(jdkMetadata.javaHome.toString() + appendPath)

        def compatibilityVersion = [curJdk, jdkMetadata].collect { it.languageVersion }.min()

        buildFile << """
            compileJava {
                options.fork = true
                ${configure.replace("<path>", path)}
                sourceCompatibility = "${compatibilityVersion}"
                targetCompatibility = "${compatibilityVersion}"
            }
        """

        file("src/main/java/Foo.java") << """
            public class Foo {}
        """

        def task = ":compileJava"

        when:
        withInstallations(jdkMetadata).run(task)
        def events = toolchainEvents(task)
        then:
        executedAndNotSkipped(task)
        assertToolchainUsages(events, jdkMetadata, "JavaCompiler")

        when:
        withInstallations(jdkMetadata).run(task)
        events = toolchainEvents(task)
        then:
        skipped(task)
        assertToolchainUsages(events, jdkMetadata, "JavaCompiler")

        where:
        option       | configure                                       | appendPath
        "java home"  | 'options.forkOptions.javaHome = file("<path>")' | ''
        "executable" | 'options.forkOptions.executable = "<path>"'     | OperatingSystem.current().getExecutableName('/bin/javac')
    }

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
        withInstallations(jdkMetadata).run(task)
        def events = toolchainEvents(task)
        then:
        executedAndNotSkipped(task)
        assertToolchainUsages(events, jdkMetadata, "JavaLauncher")

        when:
        withInstallations(jdkMetadata).run(task)
        events = toolchainEvents(task)
        then:
        skipped(task)
        assertToolchainUsages(events, jdkMetadata, "JavaLauncher")
    }

    def "emits toolchain usages for test that configures executable path overriding toolchain java extension"() {
        JvmInstallationMetadata jdkMetadata1 = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentVersion)
        JvmInstallationMetadata jdkMetadata2 = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.getDifferentVersion(jdkMetadata1.languageVersion))

        def minJdk = [jdkMetadata1, jdkMetadata2].min { it.languageVersion }
        def maxJdk = [jdkMetadata1, jdkMetadata2].max { it.languageVersion }

        configureJavaPluginToolchainVersion(minJdk)

        buildFile << """
            def javaExecutable = javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(${maxJdk.languageVersion.majorVersion})
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
        withInstallations(minJdk, maxJdk).run(task)
        def events = toolchainEvents(task)
        then:
        executedAndNotSkipped(task)
        assertToolchainUsages(events, maxJdk, "JavaLauncher")

        when:
        withInstallations(minJdk, maxJdk).run(task)
        events = toolchainEvents(task)
        then:
        skipped(task)
        assertToolchainUsages(events, maxJdk, "JavaLauncher")
    }

    @Issue("https://github.com/gradle/gradle/issues/21368")
    def "emits toolchain usages when configuring toolchains for #kotlinPlugin Kotlin plugin '#kotlinPluginVersion'"() {
        JvmInstallationMetadata jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentVersion)

        given:
        // override setup
        buildFile.text = """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "$kotlinPluginVersion"
            }
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

        and:
        def kotlinVersionNumber = VersionNumber.parse(kotlinPluginVersion)
        def isKotlin1dot6 = kotlinVersionNumber.baseVersion < VersionNumber.parse("1.7.0")
        def isKotlin1dot8 = kotlinVersionNumber.baseVersion >= VersionNumber.parse("1.8.0")

        when:
        if (isKotlin1dot6) {
            def wrapUtilWarning = "The org.gradle.util.WrapUtil type has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/current/userguide/upgrading_version_7.html#org_gradle_util_reports_deprecations"
            if (GradleContextualExecuter.isConfigCache()) {
                executer.expectDocumentedDeprecationWarning(wrapUtilWarning)
            } else {
                executer.beforeExecute {
                    executer.expectDocumentedDeprecationWarning(wrapUtilWarning)
                }
            }
            executer.expectDocumentedDeprecationWarning(
                "The org.gradle.api.plugins.WarPluginConvention type has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#base_convention_deprecation")
            executer.expectDocumentedDeprecationWarning(
                "The AbstractCompile.destinationDir property has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Please use the destinationDirectory property instead. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#compile_task_wiring")
            executer.expectDocumentedDeprecationWarning(
                "The Project.getConvention() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions")
            executer.expectDocumentedDeprecationWarning(
                "The org.gradle.api.plugins.Convention type has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions")
        }
        withInstallations(jdkMetadata).run(":compileKotlin", ":test")
        def eventsOnCompile = toolchainEvents(":compileKotlin")
        def eventsOnTest = toolchainEvents(":test")

        then:
        executedAndNotSkipped(":compileKotlin", ":test")
        println(eventsOnCompile)
        if (isKotlin1dot8) {
            // Kotlin 1.8 uses both launcher and compiler
            assertToolchainUsages(eventsOnCompile, jdkMetadata, "JavaLauncher", "JavaCompiler")
        } else {
            // The tool is a launcher with Kotlin < 1.8, because it runs own compilation in a Java VM
            assertToolchainUsages(eventsOnCompile, jdkMetadata, "JavaLauncher")
        }
        // Even though we only configure the toolchain within the `kotlin` block,
        // it actually affects the java launcher selected by the test task.
        assertToolchainUsages(eventsOnTest, jdkMetadata, "JavaLauncher")

        when:
        if (isKotlin1dot6 && GradleContextualExecuter.notConfigCache) {
            executer.expectDocumentedDeprecationWarning(
                "The Project.getConvention() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions")
            executer.expectDocumentedDeprecationWarning(
                "The org.gradle.api.plugins.Convention type has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions")
        }
        withInstallations(jdkMetadata).run(":compileKotlin", ":test")
        eventsOnCompile = toolchainEvents(":compileKotlin")
        eventsOnTest = toolchainEvents(":test")

        then:
        if (isKotlin1dot6 && Jvm.current().javaVersion.java8 && GradleContextualExecuter.configCache) {
            // For Kotlin 1.6 the compilation is not up-to-date with configuration caching when running on Java 8
            executedAndNotSkipped(":compileKotlin")
        } else {
            skipped(":compileKotlin", ":test")
        }
        assertToolchainUsages(eventsOnCompile, jdkMetadata, "JavaLauncher")
        assertToolchainUsages(eventsOnTest, jdkMetadata, "JavaLauncher")

        where:
        kotlinPlugin | _
        "1.6"        | _
        "1.7"        | _
        "latest"     | _

        kotlinPluginVersion = kotlinPlugin == "latest" ? kgpLatestVersions.last() : latestStableKotlinPluginVersion(kotlinPlugin)
    }

    def "emits toolchain usages when task fails for 'compileJava' task"() {
        def task = ":compileJava"

        JvmInstallationMetadata jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)

        configureJavaPluginToolchainVersion(jdkMetadata)

        file("src/main/java/Foo.java") << """
            public class Foo extends Oops {}
        """

        when:
        withInstallations(jdkMetadata).fails(task)
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

        configureJavaPluginToolchainVersion(jdkMetadata)

        file("src/test/java/FooTest.java") << """
            public class FooTest {
                @org.junit.Test
                public void test() { org.junit.Assert.assertEquals(1, 2); }
            }
        """

        when:
        withInstallations(jdkMetadata).fails(task)
        def events = toolchainEvents(task)
        then:
        failureDescriptionStartsWith("Execution failed for task '${task}'.")
        failureHasCause("There were failing tests.")
        assertToolchainUsages(events, jdkMetadata, "JavaLauncher")
    }

    def "emits toolchain usages when task fails for 'javadoc' task"() {
        def task = ":javadoc"

        JvmInstallationMetadata jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)

        configureJavaPluginToolchainVersion(jdkMetadata)

        file("src/main/java/Foo.java") << """
            /**
             * This is a {@link Oops} class.
             */
            public class Foo {}
        """

        when:
        withInstallations(jdkMetadata).fails(task)
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
        withInstallations(jdkMetadata).run(":myTask")
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

    private static String latestStableKotlinPluginVersion(String major) {
        return kgpLatestVersions.findAll { it.startsWith(major) && !it.contains("-") }.last()
    }
}
