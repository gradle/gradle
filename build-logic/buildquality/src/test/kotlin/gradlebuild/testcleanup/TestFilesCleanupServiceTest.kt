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
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.io.File


@EnabledIfEnvironmentVariable(named = "CI", matches = ".*", disabledReason = "Tests rely on setup conditional on CI in gradlebuild.ci-reporting plugin running first, these test should only run if CI env var is present (it's value doesn't matter)")
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
            include(":successful-test-with-leftover")
            include(":failed-report-with-leftover")
            include(":flaky-test-with-leftover")
            include(":flaky-test-without-leftover")
            include(":successful-report")
            """.trimIndent()
        )

        fun File.writeFlakyTest(fail: Boolean) {
            mkdirsAndWriteText(
                """
            class FlakyTest {
                @org.junit.jupiter.api.Test
                public void test() {
                    ${if (fail) "throw new IllegalStateException();" else ""}
                }
            }
                """.trimIndent()
            )
        }

        projectDir.resolve("failed-test-with-leftover/src/test/java/FlakyTest.java").writeFlakyTest(true)
        projectDir.resolve("flaky-test-with-leftover/src/test/java/FlakyTest.java").writeFlakyTest(true)
        projectDir.resolve("flaky-test-without-leftover/src/test/java/FlakyTest.java").writeFlakyTest(true)
        projectDir.resolve("successful-test-with-leftover/src/test/java/FlakyTest.java").writeFlakyTest(false)

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

            project(":failed-test-with-leftover").configureTestWithLeftover(false, true)
            project(":flaky-test-with-leftover").configureTestWithLeftover(true, true)
            project(":flaky-test-without-leftover").configureTestWithLeftover(true, false)
            project(":successful-test-with-leftover").configureTestWithLeftover(false, true)

            fun Project.configureTestWithLeftover(ignoreFailures: Boolean, hasLeftover: Boolean) {
                apply(plugin = "java-library")
                repositories {
                    mavenCentral()
                }

                dependencies {
                    "testImplementation"("org.junit.jupiter:junit-jupiter-engine:5.8.1")
                }

                tasks.named<Test>("test").configure {
                    this.ignoreFailures = ignoreFailures
                    if (hasLeftover) {
                        doFirst {
                            project.layout.buildDirectory.file("tmp/teŝt files/leftover/leftover").get().asFile.apply {
                                parentFile.mkdirs()
                                createNewFile()
                            }
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
                        project.touchInBuildDir("tmp/teŝt files/leftover/leftover")
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

    private
    fun run(vararg args: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withTestKitDir(projectDir.resolve("test-kit"))
        .withPluginClasspath()
        .forwardOutput()
        .withArguments(*args)

    private
    fun assertArchivedFilesSeen(vararg archiveFileNames: String) {
        val rootDirFiles = projectDir.resolve("build").walk().toList()

        archiveFileNames.forEach { fileName ->
            assertTrue(rootDirFiles.any { it.name == fileName })
        }
    }

    private
    fun assertLeftoverFilesCleanedUpEventually(vararg leftoverFiles: String) {
        leftoverFiles.forEach {
            assertTrue(projectDir.resolve(it).walk().filter { it.isFile }.toList().isEmpty())
        }
    }

    @Test
    fun `fail build if leftover file found and test passes`() {
        val result = run(":successful-test-with-leftover:test", "--no-watch-fs").buildAndFail()
        assertEquals(TaskOutcome.SUCCESS, result.task(":successful-test-with-leftover:test")!!.outcome)

        assertEquals(1, StringUtils.countMatches(result.output, "Found non-empty test files dir"))
        assertEquals(1, StringUtils.countMatches(result.output, "Failed to stop service 'testFilesCleanupBuildService'"))
        result.output.assertContains("successful-test-with-leftover/build/tmp/teŝt files/leftover")

        assertLeftoverFilesCleanedUpEventually("successful-test-with-leftover/build/tmp/teŝt files")
    }

    @Test
    fun `flaky tests without leftovers get reports achieved`() {
        val result = run(":flaky-test-without-leftover:test", "--no-watch-fs").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":flaky-test-without-leftover:test")!!.outcome)

        assertArchivedFilesSeen("report-flaky-test-without-leftover-test.zip")
    }

    @Test
    fun `leftover files are archived when test fails`() {
        val result = run(
            ":failed-report-with-leftover:test",
            ":successful-report:test",
            ":failed-test-with-leftover:test",
            "--continue",
            "--no-watch-fs"
        ).buildAndFail()
        assertEquals(TaskOutcome.SUCCESS, result.task(":successful-report:test")!!.outcome)
        assertEquals(TaskOutcome.FAILED, result.task(":failed-report-with-leftover:test")!!.outcome)
        assertEquals(TaskOutcome.FAILED, result.task(":failed-test-with-leftover:test")!!.outcome)

        // leftover files failed tests are reported but not counted as an exception, but cleaned up eventually
        assertEquals(1, StringUtils.countMatches(result.output, "Found non-empty test files dir"))
        assertEquals(1, StringUtils.countMatches(result.output, "Failed to stop service 'testFilesCleanupBuildService'"))
        result.output.assertContains("failed-report-with-leftover/build/tmp/teŝt files/leftover")
        result.output.assertContains("failed-test-with-leftover/build/tmp/teŝt files/leftover")

        assertArchivedFilesSeen(
            "report-failed-test-with-leftover-test.zip",
            "report-failed-report-with-leftover-reports.zip",
            "report-failed-test-with-leftover-leftover.zip",
            "report-successful-report-reports.zip"
        )
        assertLeftoverFilesCleanedUpEventually(
            "failed-report-with-leftover/build/tmp/teŝt files",
            "successful-report/build/tmp/teŝt files",
            "failed-test-with-leftover/build/tmp/teŝt files"
        )
    }

    @Test
    fun `build does not fail if a flaky test has leftover files`() {
        val result = run(":flaky-test-with-leftover:test", "--no-watch-fs").build()

        // leftover files failed tests are reported but not counted as an exception, but cleaned up eventually
        assertEquals(1, StringUtils.countMatches(result.output, "Leftover files"))
        result.output.assertContains("flaky-test-with-leftover/build/tmp/teŝt files/leftover")

        assertArchivedFilesSeen(
            "report-flaky-test-with-leftover-test.zip",
            "report-flaky-test-with-leftover-leftover.zip"
        )

        assertLeftoverFilesCleanedUpEventually("flaky-test-with-leftover/build/tmp/teŝt files")
    }

    private
    fun String.assertContains(text: String) {
        assertTrue(contains(text)) {
            "Did not find expected error message in $this"
        }
    }
}
