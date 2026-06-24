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

package gradlebuild.basics.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File


class KotlinSourceParserTest {

    @TempDir
    lateinit var tempDir: File

    /**
     * Each open [KotlinSourceParser.ParsedKotlinFiles] keeps its own project environment alive, so two of them open at
     * once must share the single, reference-counted application environment (the pattern binary-compatibility uses, with
     * one parse per source root). The assertions track the actual lifecycle: one environment is created and reused while
     * both parses are open, stays alive until the last one closes, then is disposed.
     */
    @Test
    fun `shares a single environment across overlapping parses and disposes it when the last one closes`() {
        val rootA = sourceRoot("a", "Apple.kt", "package a\n\nclass Apple")
        val rootB = sourceRoot("b", "Banana.kt", "package b\n\nclass Banana")
        val parser = KotlinSourceParser()

        val parsedA = parser.parseSourceRoots(listOf(rootA))
        val environment = SharedKotlinApplicationEnvironment.environment
        assertNotNull(environment, "environment is created with the first parse")

        val parsedB = parser.parseSourceRoots(listOf(rootB))
        assertSame(
            environment, SharedKotlinApplicationEnvironment.environment,
            "a second overlapping parse reuses the same environment instead of creating another"
        )

        // both overlapping parses parse correctly against the shared environment
        assertEquals(listOf("Apple.kt"), parsedA.ktFiles.map { it.name })
        assertEquals(listOf("Banana.kt"), parsedB.ktFiles.map { it.name })

        parsedA.close()
        assertNotNull(SharedKotlinApplicationEnvironment.environment, "environment stays alive while another parse is still open")

        parsedB.close()
        assertNull(SharedKotlinApplicationEnvironment.environment, "environment is disposed once the last parse closes")
    }

    /**
     * Closing the last open parse disposes the shared application environment and resets the global `ApplicationManager`,
     * so a later parse has to transparently recreate it - something the old, never-disposed environment never had to do.
     */
    @Test
    fun `recreates a fresh environment for a parse that starts after the previous one was disposed`() {
        val parser = KotlinSourceParser()

        val firstRoot = sourceRoot("first", "First.kt", "package p\n\nclass First")
        val firstEnvironment = parser.parseSourceRoots(listOf(firstRoot)).use { parsed ->
            assertEquals(listOf("First.kt"), parsed.ktFiles.map { it.name })
            SharedKotlinApplicationEnvironment.environment
        }
        assertNotNull(firstEnvironment, "environment is created for the first parse")
        assertNull(SharedKotlinApplicationEnvironment.environment, "environment is disposed after the first parse closes")

        val secondRoot = sourceRoot("second", "Second.kt", "package p\n\nclass Second")
        parser.parseSourceRoots(listOf(secondRoot)).use { parsed ->
            val secondEnvironment = SharedKotlinApplicationEnvironment.environment
            assertNotNull(secondEnvironment, "a fresh environment is created for the second parse")
            assertNotSame(firstEnvironment, secondEnvironment, "the second parse builds a fresh environment, not the disposed one")
            assertEquals(listOf("Second.kt"), parsed.ktFiles.map { it.name })
        }
    }

    private
    fun sourceRoot(name: String, fileName: String, code: String): File =
        File(tempDir, name).apply {
            resolve(fileName).apply {
                parentFile.mkdirs()
                writeText(code)
            }
        }
}
