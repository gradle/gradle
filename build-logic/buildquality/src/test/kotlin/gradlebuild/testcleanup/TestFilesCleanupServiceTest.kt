/*
 * Copyright 2021 the original author or authors.
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

package gradlebuild.testcleanup

import org.gradle.internal.impldep.org.apache.commons.lang.StringUtils
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File


class TestFilesCleanupServiceTest {
    @TempDir
    lateinit var projectDir: File

    private
    fun File.mkdirsAndWriteText(text: String) {
        parentFile.mkdirs()
        writeText(text)
    }

    @BeforeEach
    fun setUp() {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            include(":failed-test-with-leftover")
            include(":failed-report-with-leftover")
            include(":flaky-test-with-leftover")
            include(":successful-report")
            """.trimIndent()
        )

        fun File.writeFailedTestWithLeftover() {
            mkdirsAndWriteText(
                """
            class FailedTestWithLeftover {
                @org.junit.jupiter.api.Test
                public void test() {
                    throw new IllegalStateException();
                }
            }
            """.trimIndent()
            )
        }

        projectDir.resolve("failed-test-with-leftover/src/test/java/FailedTestWithLeftover.java").writeFailedTestWithLeftover()
        projectDir.resolve("flaky-test-with-leftover/src/test/java/FailedTestWithLeftover.java").writeFailedTestWithLeftover()

        projectDir.resolve("build.gradle.kts").writeText(
            """
            import org.gradle.build.event.BuildEventsListenerRegistry
            import org.gradle.api.internal.project.ProjectInternal
            import org.gradle.api.internal.tasks.testing.TestExecuter
            import org.gradle.api.internal.tasks.testing.TestExecutionSpec
            import org.gradle.api.internal.tasks.testing.TestResultProcessor

            plugins {
                id("gradlebuild.ci-reporting")
            }

            subprojects {
                apply(plugin = "gradlebuild.ci-reporting")
            }

            project(":failed-test-with-leftover").configureTestWithLeftover(false)
            project(":flaky-test-with-leftover").configureTestWithLeftover(true)

            fun Project.configureTestWithLeftover(ignoreFailures: Boolean) {
                apply(plugin = "java-library")
                repositories {
                    mavenCentral()
                }

                dependencies {
                    "testImplementation"("org.junit.jupiter:junit-jupiter-engine:5.8.1")
                }

                tasks.named<Test>("test").configure {
                    this.ignoreFailures = ignoreFailures
                    doFirst {
                        project.layout.buildDirectory.file("tmp/test files/leftover/leftover").get().asFile.apply {
                            parentFile.mkdirs()
                            createNewFile()
                        }
                    }
                    useJUnitPlatform()
                }
            }

            project(":failed-report-with-leftover") {
                registerTestWithLeftover()
            }
            project(":successful-report") {
                registerTestWithLeftover()
            }

            open class TestWithLeftover: AbstractTestTask() {
                fun Project.touchInBuildDir(path:String) {
                    layout.buildDirectory.file(path).get().asFile.apply {
                        parentFile.mkdirs()
                        createNewFile()
                    }
                }
                override fun executeTests() {
                    project.touchInBuildDir( "reports/report.html")

                    if (project.name == "failed-report-with-leftover") {
                        project.touchInBuildDir("tmp/test files/leftover/leftover")
                        throw IllegalStateException()
                    }
                }
                protected override fun createTestExecuter() = object: TestExecuter<TestExecutionSpec> {
                    override fun execute(s:TestExecutionSpec, t: TestResultProcessor) {}
                    override fun stopNow() {}
                }
                protected override fun createTestExecutionSpec() = object: TestExecutionSpec {}
            }

            fun Project.registerTestWithLeftover() {
                tasks.register<TestWithLeftover>("test") {
                    binaryResultsDirectory.set(project.layout.buildDirectory.dir("binaryResultsDirectory"))
                    reports.html.outputLocation.set(project.layout.buildDirectory.dir("reports"))
                    reports.junitXml.required.set(false)
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `fail build if leftover file found with test failing`() {
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withTestKitDir(projectDir.resolve("test-kit"))
            .withPluginClasspath()
            .forwardOutput()
            .withArguments(
                ":failed-report-with-leftover:test",
                ":successful-report:test",
                ":failed-test-with-leftover:test",
                "--continue"
            )
            .buildAndFail()

        // leftover files failed tests are reported but not counted as an exception, but cleaned up eventually
        assertEquals(1, StringUtils.countMatches(result.output, "Found non-empty test files dir"))
        result.output.assertContains("failed-report-with-leftover/build/tmp/test files/leftover")
        result.output.assertContains("failed-test-with-leftover/build/tmp/test files/leftover")
        val rootDirFiles = projectDir.resolve("build").walk().toList()

        // assert the zip file in place
        assertTrue(rootDirFiles.any { it.name == "report-failed-test-with-leftover-test.zip" })
        assertTrue(rootDirFiles.any { it.name == "report-failed-report-with-leftover-leftover.zip" })
        assertTrue(rootDirFiles.any { it.name == "report-failed-report-with-leftover-reports.zip" })
        assertTrue(rootDirFiles.any { it.name == "report-failed-test-with-leftover-leftover.zip" })
        assertTrue(rootDirFiles.none { it.name.contains("successful-report") })

        // assert the leftover files are eventually cleaned up
        assertTrue(projectDir.resolve("failed-report-with-leftover/build/tmp/test files").walk().filter { it.isFile }.toList().isEmpty())
        assertTrue(projectDir.resolve("successful-report/build/tmp/test files").walk().filter { it.isFile }.toList().isEmpty())
        assertTrue(projectDir.resolve("failed-test-with-leftover/build/tmp/test files").walk().filter { it.isFile }.toList().isEmpty())
    }

    @Test
    fun `not fail build if leftover file found with flaky tests`() {
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withTestKitDir(projectDir.resolve("test-kit"))
            .withPluginClasspath()
            .forwardOutput()
            .withArguments(":flaky-test-with-leftover:test")
            .build()

        // leftover files failed tests are reported but not counted as an exception, but cleaned up eventually
        assertEquals(1, StringUtils.countMatches(result.output, "Leftover files"))
        result.output.assertContains("flaky-test-with-leftover/build/tmp/test files/leftover")
        val rootDirFiles = projectDir.resolve("build").walk().toList()

        // assert the zip file in place
        assertTrue(rootDirFiles.any { it.name == "report-flaky-test-with-leftover-test.zip" })
        assertTrue(rootDirFiles.any { it.name == "report-flaky-test-with-leftover-leftover.zip" })

        // assert the leftover files are eventually cleaned up
        assertTrue(projectDir.resolve("flaky-test-with-leftover/build/tmp/test files").walk().filter { it.isFile }.toList().isEmpty())
    }

    private
    fun String.assertContains(text: String) {
        assertTrue(contains(text)) {
            "Did not find expected error message in $this"
        }
    }
}
