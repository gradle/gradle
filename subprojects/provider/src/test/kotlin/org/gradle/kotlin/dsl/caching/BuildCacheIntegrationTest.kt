/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.caching

import org.gradle.kotlin.dsl.fixtures.LeaksFileHandles
import org.gradle.kotlin.dsl.integration.normalisedPath

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File


class BuildCacheIntegrationTest : AbstractScriptCachingIntegrationTest() {

    @LeaksFileHandles("on the separate Gradle homes")
    @Test
    fun `build cache integration is enabled via project property`() {

        val buildCacheDir =
            existing("build-cache")

        val settingsFile =
            withLocalBuildCacheSettings(buildCacheDir)

        val expectedOutput = "***42***"

        val buildFile =
            withBuildScript("""
                println("$expectedOutput")
            """)

        val cachedSettingsFile =
            cachedSettingsFile(settingsFile, hasBody = true)

        val cachedBuildFile =
            cachedBuildFile(buildFile, hasBody = true)

        val withBuildCacheIntegration = "-Porg.gradle.kotlin.dsl.caching.buildcache=true"

        // Cache miss with a fresh Gradle home, script cache will be pushed to build cache
        buildWithUniqueGradleHome("--build-cache", withBuildCacheIntegration).apply {

            compilationCache {
                misses(cachedSettingsFile)
                misses(cachedBuildFile)
            }

            assertThat(output, containsString(expectedOutput))
        }

        // Cache hit from build cache
        buildWithUniqueGradleHome("--build-cache", withBuildCacheIntegration).apply {

            compilationCache {
                misses(cachedSettingsFile)
                hits(cachedBuildFile)
            }

            assertThat(output, containsString(expectedOutput))
        }

        // Cache miss without build cache integration
        buildWithUniqueGradleHome("--build-cache").apply {

            compilationCache {
                misses(cachedSettingsFile)
                misses(cachedBuildFile)
            }

            assertThat(output, containsString(expectedOutput))
        }
    }

    private
    fun withLocalBuildCacheSettings(buildCacheDir: File): File =
        withSettings("""
            buildCache {
                local(DirectoryBuildCache::class.java) {
                    directory = file("${buildCacheDir.normalisedPath}")
                    isEnabled = true
                    isPush = true
                }
            }
        """)
}
