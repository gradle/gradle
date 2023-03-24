/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild.packaging

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File


class GradleDistributionInstallTest {
    @TempDir
    private
    lateinit var temporaryFolder: File

    private
    lateinit var target: File

    private
    lateinit var projectRoot: File

    @BeforeEach
    fun setup() {
        target = File(temporaryFolder, "target")
        target.mkdir()

        projectRoot = File(temporaryFolder, "gradle")

        createMinimalDistribution()
    }

    @Test
    fun `installs into empty dir`() {
        assertSucceeds()
    }

    @Test
    fun `installs to non-existing dir`() {
        target = File(target, "non-existing")

        assertSucceeds()
    }

    @Test
    fun `installs into previous distribution`() {
        assertSucceeds()
        assertSucceeds()
    }

    @Test
    fun `installs into something that looks like previous distribution`() {
        target.resolve("bin").apply {
            mkdir()
            File(this, "gradle").writeText("stub")
            File(this, "gradle.exe").writeText("stub")
        }

        target.resolve("lib").apply {
            mkdir()
            File(this, "gradle-8.0.2.jar").writeText("stub")
            File(this, "all-deps-in-the-world-1.2.2.jar").writeText("stub")
        }

        assertSucceeds()
    }

    @Test
    fun `does not install to a file`() {
        val file = File(target, "some_file.txt").also {
            it.writeText("some content")
        }
        target = file

        assertFails("Install directory $file is not valid: it is actually a file")
        assertTrue(file.exists())
    }

    @Test
    fun `does not install to non-empty dir without lib`() {
        target.resolve("bin").apply {
            mkdir()
            File(this, "gradle").writeText("stub")
            File(this, "gradle.exe").writeText("stub")
        }

        assertFails("Install directory $target does not look like a Gradle installation. Cannot delete it to install.")
        assertTargetIsPreserved()
    }

    @Test
    fun `does not install to non-empty dir with empty lib`() {
        target.resolve("bin").apply {
            mkdir()
            File(this, "gradle").writeText("stub")
            File(this, "gradle.exe").writeText("stub")
        }

        target.resolve("lib").apply {
            mkdir()
        }

        assertFails("Install directory $target does not look like a Gradle installation. Cannot delete it to install.")
        assertTargetIsPreserved()
    }

    @Test
    fun `does not install to non-empty dir without gradle executables`() {
        target.resolve("lib").apply {
            mkdir()
            File(this, "gradle-8.0.2.jar").writeText("stub")
            File(this, "all-deps-in-the-world-1.2.2.jar").writeText("stub")
        }

        assertFails("Install directory $target does not look like a Gradle installation. Cannot delete it to install.")
        assertTargetIsPreserved()
    }

    @Test
    fun `does not install to non-empty dir without gradle executables and empty bin`() {
        target.resolve("bin").apply {
            mkdir()
        }

        target.resolve("lib").apply {
            mkdir()
            File(this, "gradle-8.0.2.jar").writeText("stub")
            File(this, "all-deps-in-the-world-1.2.2.jar").writeText("stub")
        }
        assertFails("Install directory $target does not look like a Gradle installation. Cannot delete it to install.")
        assertTargetIsPreserved()
    }

    @Test
    fun `does not install to dir with other executables`() {
        target.resolve("bin").apply {
            mkdir()
            File(this, "gradle").writeText("stub")
            File(this, "gradle.exe").writeText("stub")
            File(this, "python").writeText("stub")
        }

        target.resolve("lib").apply {
            mkdir()
            File(this, "gradle-8.0.2.jar").writeText("stub")
            File(this, "all-deps-in-the-world-1.2.2.jar").writeText("stub")
        }

        assertFails("Install directory $target does not look like a Gradle installation. Cannot delete it to install.")
        assertTargetIsPreserved()
    }

    @Test
    fun `does not install to dir without jars`() {
        target.resolve("bin").apply {
            mkdir()
            File(this, "gradle").writeText("stub")
            File(this, "gradle.exe").writeText("stub")
            File(this, "python").writeText("stub")
        }

        target.resolve("lib").apply {
            mkdir()
            File(this, "all-deps-in-the-world-1.2.2.jar").writeText("stub")
        }

        assertFails("Install directory $target does not look like a Gradle installation. Cannot delete it to install.")
        assertTargetIsPreserved()
    }

    private
    fun runner() = GradleRunner.create()
        .withProjectDir(projectRoot)
        .withPluginClasspath()
        .forwardOutput()
        .withArguments("install", "-Pgradle_installPath=$target")

    private
    fun assertSucceeds() {
        runner().build()
        assertEquals(
            marker,
            target.resolve("bin/gradlew.bat").readText(),
        )
    }

    private
    fun assertFails(error: String) {
        val result = runner().buildAndFail()
        assertTrue(
            result.output.contains(error)
        )
    }

    private
    fun assertTargetIsPreserved() {
        assertTrue((target.list()?.size ?: 0) > 0)
        assertTrue(target.walk().filter { it.isFile }.all { it.readText() == "stub" })
    }

    private
    fun createMinimalDistribution() {
        projectRoot.mkdir()
        File(projectRoot, "build.gradle.kts").writeText("""
        plugins {
            id("gradlebuild.install")
        }

        val gradleScriptPath by configurations.creating
        val coreRuntimeClasspath by configurations.creating
        val runtimeClasspath by configurations.creating
        val agentsRuntimeClasspath by configurations.creating

        val runtimeApiInfoJar by tasks.registering(Jar::class){
            archiveVersion.set("8.2")
            archiveBaseName.set("gradle-runtime-api")
            destinationDirectory.set(layout.buildDirectory.dir("jars"))
        }

        dependencies {
            gradleScriptPath(files("gradlew.bat"))
        }
        """)
        File(projectRoot, "version.txt").writeText("8.2")
        File(projectRoot, "gradlew.bat").writeText(marker)
    }

    private
    val marker = "I'm a marker text"
}
