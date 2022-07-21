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
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.jvm.toolchain.internal.operations.JavaToolchainUsageProgressDetails
import org.gradle.test.fixtures.file.TestFile

class JavaToolchainBuildOperationsIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "toolchain usage events are emitted for java compilation"() {
        def jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)
        buildscriptWithToolchain(jdkMetadata)

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        runWithToolchainConfigured(jdkMetadata, "compileJava")
        def events = eventsFor(":compileJava")
        then:
        events.size() > 0
        events.each { usageEvent ->
            assertToolchainUsage("javac", jdkMetadata, usageEvent)
        }

        when:
        runWithToolchainConfigured(jdkMetadata, "compileJava")
        events = eventsFor(":compileJava")
        then:
        skipped(":compileJava")
        events.size() > 0
        events.each { usageEvent ->
            assertToolchainUsage("javac", jdkMetadata, usageEvent)
        }
    }

    def "toolchain usage events are emitted for test"() {
        def jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)
        buildscriptWithToolchain(jdkMetadata)

        file("src/test/java/FooTest.java") << """
            public class FooTest {
                @org.junit.Test
                public void test() {}
            }
        """

        when:
        runWithToolchainConfigured(jdkMetadata, "check")
        def compileTestEvents = eventsFor(":compileTestJava")
        def testEvents = eventsFor(":test")
        then:
        compileTestEvents.size() > 0
        compileTestEvents.each { usageEvent ->
            assertToolchainUsage("javac", jdkMetadata, usageEvent)
        }
        testEvents.size() > 0
        testEvents.each { usageEvent ->
            assertToolchainUsage("java", jdkMetadata, usageEvent)
        }
    }

    def "toolchain usage events are emitted for javadoc"() {
        def jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentJdk)
        buildscriptWithToolchain(jdkMetadata)

        file("src/main/java/Foo.java") << """
            /**
             * This is a {@code} Foo class.
             */
            public class Foo {}
        """

        when:
        runWithToolchainConfigured(jdkMetadata, "javadoc")
        def events = eventsFor(":javadoc")
        then:
        events.size() > 0
        events.each { usageEvent ->
            assertToolchainUsage("javadoc", jdkMetadata, usageEvent)
        }
    }

    // TODO: test with a custom task that uses a toolchain

    // TODO: test with a custom task that uses two toolchains

    // TODO: test with two tasks using own toolchain in one build, and each event should be attributed to the right tasks
    //  - two tasks could be in the same project
    //  - two tasks could in different sub-projects (one using Java 8 and Java 11)

    // TODO: javaCompile.options.forkOptions.javaHome = "blah" while using a toolchain

    // TODO: test with Kotlin plugin

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

    private TestFile buildscriptWithToolchain(JvmInstallationMetadata jdkMetadata) {
        buildFile << """
            apply plugin: "java"

            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'junit:junit:4.13'
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata.languageVersion.majorVersion})
                }
            }
        """
    }

    def runWithToolchainConfigured(JvmInstallationMetadata jdkMetadata, String... tasks) {
        result = executer
            .withArgument("-Porg.gradle.java.installations.paths=" + jdkMetadata.javaHome.toAbsolutePath())
            .withTasks(tasks)
            .run()
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

    private static String testClass(String className) {
        return """
            import org.junit.*;

            public class $className {
               @Test
               public void test() {
                  Assert.assertEquals(1,1);
               }
            }
        """.stripIndent()
    }
}
