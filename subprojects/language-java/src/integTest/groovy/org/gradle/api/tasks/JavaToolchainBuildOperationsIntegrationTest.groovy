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

import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.jvm.toolchain.internal.operations.JavaToolchainUsageProgressDetails
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.ToBeImplemented

class JavaToolchainBuildOperationsIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        buildFile << """
            apply plugin: "java"

            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'junit:junit:4.13'
            }
        """.stripIndent()
    }

    @ToBeImplemented("All cases are supported except up-to-dateness for the javadoc task")
    def "emit toolchain usages for a build #configureToolchain configured toolchain for '#task' task"() {
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
        """.stripIndent()

        file("src/test/java/FooTest.java") << """
            public class FooTest {
                @org.junit.Test
                public void test() {}
            }
        """.stripIndent()

        def taskPath = ":$task"

        when:
        runWithInstallation(jdkMetadata, task)
        def events = eventsFor(taskPath)
        then:
        executedAndNotSkipped(taskPath)
        assertToolchainUsages(events, jdkMetadata, tool)

        when:
        runWithInstallation(jdkMetadata, task)
        events = eventsFor(taskPath)
        then:
        skipped(taskPath)
        if (emitsWhenUpToDate) {
            assertToolchainUsages(events, jdkMetadata, tool)
        }

        where:
        task          | tool           | configureToolchain              | emitsWhenUpToDate
        "compileJava" | "JavaCompiler" | "with java plugin"              | true
        "compileJava" | "JavaCompiler" | "with per task"                 | true
        "compileJava" | "JavaCompiler" | "with java plugin and per task" | true
        "compileJava" | "JavaCompiler" | "without"                       | true
        "test"        | "JavaLauncher" | "with java plugin"              | true
        "test"        | "JavaLauncher" | "with per task"                 | true
        "test"        | "JavaLauncher" | "with java plugin and per task" | true
        "test"        | "JavaLauncher" | "without"                       | true
        "javadoc"     | "JavadocTool"  | "with java plugin"              | false // TODO: this must be fixed
        "javadoc"     | "JavadocTool"  | "with per task"                 | false // TODO: this must be fixed
        "javadoc"     | "JavadocTool"  | "with java plugin and per task" | false // TODO: this must be fixed
        "javadoc"     | "JavadocTool"  | "without"                       | false // TODO: this must be fixed
    }

    def "custom task that uses a toolchain"() {
        def task = "myToolchainTask"
        def taskPath = ":$task"

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

            tasks.register("$task", ToolchainTask) {
                outputFile = layout.buildDirectory.file("output.txt")
                launcher1 = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata.languageVersion.majorVersion})
                }
            }
        """.stripIndent()

        when:
        runWithInstallation(jdkMetadata, task)
        def events = eventsFor(taskPath)
        then:
        executedAndNotSkipped(taskPath)
        assertToolchainUsages(events, jdkMetadata, "JavaLauncher")

        // TODO: should it work with up-to-date tasks?
//        when:
//        runWithInstallation(jdkMetadata, task)
//        events = eventsFor(taskPath)
//        then:
//        skipped(taskPath)
//        assertToolchainUsages(events, jdkMetadata, "JavaLauncher")
    }

    def "custom task that uses two toolchains"() {
        def task = "myToolchainTask"
        def taskPath = ":$task"

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

            tasks.register("$task", ToolchainTask) {
                outputFile = layout.buildDirectory.file("output.txt")
                launcher1 = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata1.languageVersion.majorVersion})
                }
                launcher2 = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata2.languageVersion.majorVersion})
                }
            }
        """.stripIndent()

        def installationPaths = [jdkMetadata1, jdkMetadata2].collect { it.javaHome.toAbsolutePath().toString() }.join(",")

        when:
        runWithInstallationPaths(installationPaths, task)
        def events = eventsFor(taskPath)
        def events1 = filterByJavaVersion(events, jdkMetadata1)
        def events2 = filterByJavaVersion(events, jdkMetadata2)
        then:
        executedAndNotSkipped(taskPath)
        events.size() > 0
        assertToolchainUsages(events1, jdkMetadata1, "JavaLauncher")
        assertToolchainUsages(events2, jdkMetadata2, "JavaLauncher")

        // TODO: should it work with up-to-date tasks?
//        when:
//        runWithInstallationPaths(installationPaths, task)
//        events = eventsFor(taskPath)
//        events1 = filterByJavaVersion(events, jdkMetadata1)
//        events2 = filterByJavaVersion(events, jdkMetadata2)
//        then:
//        skipped(taskPath)
//        events.size() > 0
//        assertToolchainUsages(events1, jdkMetadata1, "JavaLauncher")
//        assertToolchainUsages(events2, jdkMetadata2, "JavaLauncher")
    }

    // TODO: test with two tasks using own toolchain in one build, and each event should be attributed to the right tasks
    //  - two tasks could be in the same project
    //  - two tasks could in different sub-projects (one using Java 8 and Java 11)

    // TODO: javaCompile.options.forkOptions.javaHome = "blah" while using a toolchain

    // TODO: test with Kotlin plugin

    def "emit toolchain usages when task fails for 'compileJava' task"() {
        def task = "compileJava"
        def taskPath = ":$task"

        JvmInstallationMetadata jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)

        configureToolchainViaJavaPlugin(jdkMetadata)

        file("src/main/java/Foo.java") << """
            public class Foo extends Oops { }
        """.stripIndent()

        when:
        runWithInstallationExpectingFailure(jdkMetadata, task)
        def events = eventsFor(taskPath)
        then:
        failureDescriptionStartsWith("Execution failed for task '${taskPath}'.")
        failureHasCause("Compilation failed; see the compiler error output for details.")
        result.assertHasErrorOutput("Foo.java:2: error: cannot find symbol")
        assertToolchainUsages(events, jdkMetadata, "JavaCompiler")
    }

    def "emit toolchain usages when task fails for 'test' task"() {
        def task = "test"
        def taskPath = ":$task"

        JvmInstallationMetadata jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)

        configureToolchainViaJavaPlugin(jdkMetadata)

        file("src/test/java/FooTest.java") << """
            public class FooTest {
                @org.junit.Test
                public void test() { org.junit.Assert.assertEquals(1, 2); }
            }
        """.stripIndent()

        when:
        runWithInstallationExpectingFailure(jdkMetadata, task)
        def events = eventsFor(taskPath)
        then:
        failureDescriptionStartsWith("Execution failed for task '${taskPath}'.")
        failureHasCause("There were failing tests.")
        assertToolchainUsages(events, jdkMetadata, "JavaLauncher")
    }

    def "emit toolchain usages when task fails for 'javadoc' task"() {
        def task = "javadoc"
        def taskPath = ":$task"

        JvmInstallationMetadata jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)

        configureToolchainViaJavaPlugin(jdkMetadata)

        file("src/main/java/Foo.java") << """
            /**
             * This is a {@link Oops} class.
             */
            public class Foo { }
        """.stripIndent()

        when:
        runWithInstallationExpectingFailure(jdkMetadata, task)
        def events = eventsFor(taskPath)
        then:
        failureDescriptionStartsWith("Execution failed for task '${taskPath}'.")
        failureCauseContains("Javadoc generation failed")
        assertToolchainUsages(events, jdkMetadata, "JavadocTool")
    }

    def "ignores toolchain events at configuration time"() {
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
        """.stripIndent()

        when:
        runWithInstallation(jdkMetadata, "myTask")
        def events = eventsFor(":myTask")

        then:
        events.size() == 0
        output.contains(jdkMetadata.javaHome.toAbsolutePath().toString())
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
        """.stripIndent()
    }

    private TestFile configureToolchainViaJavaPlugin(JvmInstallationMetadata jdkMetadata) {
        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata.languageVersion.majorVersion})
                }
            }
        """.stripIndent()
    }

    private void assertToolchainUsages(List<BuildOperationRecord.Progress> events, JvmInstallationMetadata jdkMetadata, String tool) {
        assert events.size() > 0
        events.each { usageEvent ->
            assertToolchainUsage(tool, jdkMetadata, usageEvent)
        }
    }

    private void assertToolchainUsage(String toolName, JvmInstallationMetadata jdkMetadata, BuildOperationRecord.Progress usageEvent) {
        assert usageEvent.details.toolName == toolName

        def usedToolchain = usageEvent.details.toolchain
        assert usedToolchain == [
            javaVersion: jdkMetadata.javaVersion,
            javaVendor: jdkMetadata.vendor.displayName,
            runtimeName: jdkMetadata.runtimeName,
            runtimeVersion: jdkMetadata.runtimeVersion,
            jvmName: jdkMetadata.jvmName,
            jvmVersion: jdkMetadata.jvmVersion,
            jvmVendor: jdkMetadata.jvmVendor,
            architecture: jdkMetadata.architecture,
        ]
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

    List<BuildOperationRecord.Progress> eventsFor(String taskPath) {
        def taskRecord = operations.first(ExecuteTaskBuildOperationType) {
            it.details.taskPath == taskPath
        }
        List<BuildOperationRecord.Progress> events = []
        operations.walk(taskRecord) {
            events.addAll(it.progress.findAll {
                JavaToolchainUsageProgressDetails.isAssignableFrom(it.detailsType)
            })
        }
        events
    }

    List<BuildOperationRecord.Progress> filterByJavaVersion(List<BuildOperationRecord.Progress> events, JvmInstallationMetadata jdkMetadata) {
        events.findAll { it.details.toolchain.javaVersion == jdkMetadata.javaVersion }
    }
}
