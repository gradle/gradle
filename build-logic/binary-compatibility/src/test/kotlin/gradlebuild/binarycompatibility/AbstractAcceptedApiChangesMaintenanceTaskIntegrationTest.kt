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

package gradlebuild.binarycompatibility

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.internal.TextUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.StringWriter


abstract class AbstractAcceptedApiChangesMaintenanceTaskIntegrationTest {
    @TempDir
    lateinit var projectDir: File
    lateinit var acceptedApiChangesFile: File

    @BeforeEach
    fun setUp() {
        projectDir.resolve("src").resolve("changes").mkdirs()
        acceptedApiChangesFile = projectDir.resolve("src/changes/accepted-public-api-changes.json")

        projectDir.resolve("build.gradle.kts")
            .writeText(
                """
                    plugins {
                        id("gradlebuild.binary-compatibility")
                    }

                    val verifyAcceptedApiChangesOrdering = tasks.register<gradlebuild.binarycompatibility.AlphabeticalAcceptedApiChangesTask>("verifyAcceptedApiChangesOrdering") {
                        group = "verification"
                        description = "Ensures the accepted api changes file is kept alphabetically ordered to make merging changes to it easier"
                        apiChangesFile.set(layout.projectDirectory.file("${ TextUtil.normaliseFileSeparators(acceptedApiChangesFile.absolutePath) }"))
                    }

                    val sortAcceptedApiChanges = tasks.register<gradlebuild.binarycompatibility.SortAcceptedApiChangesTask>("sortAcceptedApiChanges") {
                        group = "verification"
                        description = "Sort the accepted api changes file alphabetically"
                        apiChangesFile.set(layout.projectDirectory.file("${ TextUtil.normaliseFileSeparators(acceptedApiChangesFile.absolutePath) }"))
                    }
                """.trimIndent()
            )

        setupPluginRequirements()
    }

    /**
     * Create projects and files required for plugins applied to the project (including transitively applied plugins).
     * These files are not required to run the task itself, but are required to apply the binary-compatibility plugin.
     */
    protected
    fun setupPluginRequirements() {
        projectDir.resolve("version.txt").writeText("9999999.0") // All released versions should be lower than this
        projectDir.resolve("released-versions.json").writeText(
            """
                {
                  "latestReleaseSnapshot": {
                    "version": "7.6-20220831090819+0000",
                    "buildTime": "20220831090819+0000"
                  },
                  "latestRc": {
                    "version": "7.5-rc-5",
                    "buildTime": "20220712114039+0000"
                  },
                  "finalReleases": [
                    {
                      "version": "7.5.1",
                      "buildTime": "20220805211756+0000"
                    },
                    {
                      "version": "7.5",
                      "buildTime": "20220714124815+0000"
                    },
                    {
                      "version": "7.4.2",
                      "buildTime": "20220331152529+0000"
                    },
                    {
                      "version": "6.9.2",
                      "buildTime": "20211221172537+0000"
                    }
                  ]
                }
            """.trimIndent()
        )
    }

    protected
    fun run(vararg args: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withTestKitDir(projectDir.resolve("test-kit"))
        .withPluginClasspath()
        .forwardOutput()
        .withArguments(*args)


    protected
    fun assertChangesProperlyOrdered() {
        val result = run(":verifyAcceptedApiChangesOrdering").build()
        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":verifyAcceptedApiChangesOrdering")!!.outcome)
    }

    protected
    fun assertHasMisorderedChanges(changes: List<Change>? = null) {
        val standardError = StringWriter()
        run(":verifyAcceptedApiChangesOrdering")
            .forwardStdError(standardError)
            .buildAndFail()

        val expectedOutput = "API changes in file '${acceptedApiChangesFile.name}' should be in alphabetical order (by type and member), yet these changes were not:\n"
        val cleanupHint = "To automatically alphabetize these changes run: 'gradlew :architecture-test:sortAcceptedApiChanges'"
        with(standardError) {
            assertContains(expectedOutput)
            changes?.forEach { assertContains(it.toString()) }
            assertContains(cleanupHint)
        }
    }

    private
    fun StringWriter.assertContains(text: String) {
        Assertions.assertTrue(toString().contains(text)) {
            "Did not find expected error message in $this"
        }
    }

    protected
    data class Change(val type: String, val member: String) {
        override fun toString(): String = "Type: '$type', Member: '$member'"
    }
}
