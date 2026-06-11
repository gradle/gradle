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

package gradlebuild.incubation.action

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File


class ReleasedVersionsTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `parses versions in file order with formatted dates`() {
        val file = releasedVersionsFile(
            """
            {
              "latestReleaseSnapshot": {
                "version": "9.6.0-20260604021548+0000",
                "buildTime": "20260604021548+0000"
              },
              "latestRc": {
                "version": "9.6.0-rc-1",
                "buildTime": "20260528120535+0000"
              },
              "finalReleases": [
                {
                  "version": "9.5.1",
                  "buildTime": "20260512131942+0000"
                },
                {
                  "version": "9.5.0",
                  "buildTime": "20260428120530+0000"
                }
              ]
            }
            """.trimIndent()
        )

        val parsed = ReleasedVersions.parse(file)

        assertEquals(
            listOf(
                ReleasedVersions.ReleasedVersion("9.6.0-20260604021548+0000", "2026-06-04"),
                ReleasedVersions.ReleasedVersion("9.6.0-rc-1", "2026-05-28"),
                ReleasedVersions.ReleasedVersion("9.5.1", "2026-05-12"),
                ReleasedVersions.ReleasedVersion("9.5.0", "2026-04-28"),
            ),
            parsed
        )
    }

    @Test
    fun `builds a version to date map`() {
        val file = releasedVersionsFile(
            """
            {
              "finalReleases": [
                {
                  "version": "8.14",
                  "buildTime": "20250425110700+0000"
                },
                {
                  "version": "8.0.2",
                  "buildTime": "20230301000000+0000"
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(
            mapOf("8.14" to "2025-04-25", "8.0.2" to "2023-03-01"),
            ReleasedVersions.versionDates(file)
        )
    }

    @Test
    fun `parses an empty release list`() {
        val file = releasedVersionsFile("""{ "finalReleases": [] }""")

        assertEquals(emptyList<ReleasedVersions.ReleasedVersion>(), ReleasedVersions.parse(file))
    }

    @Test
    fun `reduces versions to their major minor component`() {
        assertEquals("9.7", ReleasedVersions.toMinor("9.7.0"))
        assertEquals("8.4", ReleasedVersions.toMinor("8.4"))
        assertEquals("8.10", ReleasedVersions.toMinor("8.10"))
        assertEquals("9.6", ReleasedVersions.toMinor("9.6.0-rc-1"))
        assertEquals("9.6", ReleasedVersions.toMinor("9.6.0-20260604021548+0000"))
        assertEquals("1.0", ReleasedVersions.toMinor("1.0-milestone-3"))
    }

    @Test
    fun `keeps single component versions unchanged`() {
        assertEquals("9", ReleasedVersions.toMinor("9"))
    }

    private
    fun releasedVersionsFile(content: String): File =
        File(tempDir, "released-versions.json").apply { writeText(content) }
}
