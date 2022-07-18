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

class JavaToolchainBuildOperationsIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "toolchain usage events are emitted for java compilation"() {
        // TODO: we probably don't need Jvm instance at all in the tests
        def otherJdk = AvailableJavaHomes.differentJdk
        def otherJdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJdk)
        buildscriptWithToolchain(otherJdk)

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        runWithToolchainConfigured(otherJdk, "compileJava")
        def events = eventsFor(":compileJava")
        then:
        events.size() > 0
        events.each { usageEvent ->
            assertToolchainUsage("javac", otherJdkMetadata, usageEvent)
        }

        when:
        runWithToolchainConfigured(otherJdk, "compileJava")
        events = eventsFor(":compileJava")
        then:
        skipped(":compileJava")
        events.size() > 0
        events.each { usageEvent ->
            assertToolchainUsage("javac", otherJdkMetadata, usageEvent)
        }
    }


    private void assertToolchainUsage(String toolName, JvmInstallationMetadata jdkMetadata, BuildOperationRecord.Progress usageEvent) {
        assert usageEvent.details.toolName == toolName

        def usedToolchain = usageEvent.details.toolchain
        assert usedToolchain == [
            languageVersion: jdkMetadata.languageVersion.toString(),
            vendor: jdkMetadata.vendor.displayName,
        ]
    }

    private TestFile buildscriptWithToolchain(Jvm someJdk) {
        buildFile << """
            apply plugin: "java"

            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'junit:junit:4.13'
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${someJdk.javaVersion.majorVersion})
                }
            }
        """
    }

    def runWithToolchainConfigured(Jvm jvm, String... tasks) {
        result = executer
            .withArgument("-Porg.gradle.java.installations.paths=" + jvm.javaHome.absolutePath)
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
