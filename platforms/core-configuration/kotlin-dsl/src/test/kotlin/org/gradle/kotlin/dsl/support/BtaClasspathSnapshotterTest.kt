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

@file:OptIn(ExperimentalBuildToolsApi::class)

package org.gradle.kotlin.dsl.support

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.buildtools.api.jvm.JvmPlatformToolchain
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream


class BtaClasspathSnapshotterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `snapshots a classpath entry identically across independent build sessions`() {
        // Compile avoidance and incremental compilation each own a separate BTA build session, yet
        // they share the produced .snapshot/.abi files by content hash and whoever writes a given
        // entry first wins. So snapshotting the same entry must not depend on which session produced
        // it — otherwise the other layer would silently read different bytes for the same input.
        val classpathEntry = jarOf("kotlin/Unit.class", "kotlin/Pair.class", "kotlin/Triple.class")

        val firstSnapshot = tmp.newFile("first.snapshot").toPath()
        val secondSnapshot = tmp.newFile("second.snapshot").toPath()

        val firstRollup = inFreshSession { jvm, session ->
            BtaClasspathSnapshotter.snapshot(jvm, session, classpathEntry, firstSnapshot)
        }
        val secondRollup = inFreshSession { jvm, session ->
            BtaClasspathSnapshotter.snapshot(jvm, session, classpathEntry, secondSnapshot)
        }

        assertThat(
            "snapshot bytes differ across build sessions",
            Files.readAllBytes(secondSnapshot).toList(),
            equalTo(Files.readAllBytes(firstSnapshot).toList())
        )
        assertThat("ABI rollup differs across build sessions", secondRollup, equalTo(firstRollup))
    }

    private fun <T> inFreshSession(action: (JvmPlatformToolchain, KotlinToolchains.BuildSession) -> T): T {
        val toolchains = KotlinToolchains.loadImplementation(javaClass.classLoader)
        val session = toolchains.createBuildSession()
        return try {
            action(toolchains.getToolchain(JvmPlatformToolchain::class.java), session)
        } finally {
            session.close()
        }
    }

    private fun jarOf(vararg classResources: String): Path {
        val jar = tmp.newFile("entry.jar")
        JarOutputStream(jar.outputStream()).use { out ->
            classResources.forEach { resource ->
                out.putNextEntry(JarEntry(resource))
                javaClass.classLoader.getResourceAsStream(resource)!!.use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        return jar.toPath()
    }
}
