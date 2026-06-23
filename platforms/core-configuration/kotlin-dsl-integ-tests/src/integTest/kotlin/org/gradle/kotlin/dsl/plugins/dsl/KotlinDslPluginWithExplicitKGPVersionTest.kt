/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.kotlin.dsl.plugins.dsl

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.kotlin.dsl.embeddedKotlinVersion
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.util.internal.VersionNumber
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.Closeable
import java.net.HttpURLConnection.HTTP_MOVED_TEMP
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_OK


/**
 * Tests that the `kotlin-dsl` plugin can be applied alongside an explicit, different version
 * of the `org.jetbrains.kotlin.jvm` plugin.
 *
 * We aim to test with both an older and a newer version of KGP, than the Kotlin version
 * currently embedded in Gradle, because we have observed that they can behave differently.
 *
 * When no real newer Kotlin version is available, a synthetic version is used.
 * A [SyntheticKgpRepo] serves the synthetic version's metadata by rewriting
 * the embedded version's artifacts from the Gradle Plugin Portal.
 */
@RunWith(Parameterized::class)
class KotlinDslPluginWithExplicitKGPVersionTest(
    private val kotlinVersionString: String,
    private val versionLabel: String
) : AbstractKotlinIntegrationTest() {

    companion object {

        @Parameterized.Parameters(name = "KGP {0} ({1})")
        @JvmStatic
        fun testedKotlinVersions(): List<Array<String>> {
            val embeddedVersion = VersionNumber.parse(embeddedKotlinVersion)
            val kgpVersions = KotlinGradlePluginVersions()

            val latestKnown = kgpVersions.latest
            val latestKnownVersion = VersionNumber.parse(latestKnown)

            // Latest available version newer than embedded, or a synthetic next minor version
            val newerVersion = if (latestKnownVersion > embeddedVersion) {
                arrayOf(latestKnown, "real")
            } else {
                arrayOf("${embeddedVersion.major}.${embeddedVersion.minor + 1}.0", "synthetic")
            }

            // Latest stable version older than embedded (always differs)
            val latestOlderStable = kgpVersions.latestsStable
                .lastOrNull { VersionNumber.parse(it) < embeddedVersion }
                ?.let { arrayOf(it, "real") }

            return listOfNotNull(latestOlderStable, newerVersion)
        }

        private fun isSynthetic(label: String): Boolean = label == "synthetic"
    }

    private var syntheticKgpRepo: SyntheticKgpRepo? = null

    @Test
    fun `can apply kotlin-dsl plugin with explicit different kotlin-jvm plugin version`() {

        if (isSynthetic(versionLabel)) {
            setupSyntheticKgpRepo()
        }

        try {
            withBuildScript(
                """

                plugins {
                    `kotlin-dsl`
                    id("org.jetbrains.kotlin.jvm") version "$kotlinVersionString"
                }

                $repositoriesBlock

                """
            )

            withFile(
                "src/main/kotlin/code.kt",
                """

                import org.gradle.api.Plugin
                import org.gradle.api.Project

                class MyPlugin : Plugin<Project> {
                    override fun apply(project: Project) {
                        println("applied!")
                    }
                }

                """
            )

            build("classes")
        } finally {
            syntheticKgpRepo?.close()
        }
    }

    private fun setupSyntheticKgpRepo() {
        syntheticKgpRepo = SyntheticKgpRepo(kotlinVersionString, embeddedKotlinVersion)
        syntheticKgpRepo!!.start()

        val initScript = file(".integTest/synthetic-kgp-repo.init.gradle")
        initScript.parentFile.mkdirs()
        initScript.writeText(
            """
            beforeSettings { settings ->
                settings.pluginManagement {
                    repositories {
                        maven {
                            url = uri("${syntheticKgpRepo!!.url}")
                        }
                    }
                }
            }
            """
        )
        executer.beforeExecute {
            it.usingInitScript(initScript)
        }
    }

    /**
     * A lightweight Maven repository proxy that serves a synthetic version of the KGP plugin.
     *
     * Metadata files (POM, .module) are fetched from the Plugin Portal, have their version strings
     * rewritten, and are served with the modified content. All other files (jars, checksums,
     * etc.) are redirected to the upstream URL via HTTP 302.
     */
    class SyntheticKgpRepo(
        private val syntheticVersion: String,
        private val realVersion: String,
        private val upstreamBaseUrl: String = RepoScriptBlockUtil.gradlePluginRepositoryMirrorUrl()
    ) : Closeable {

        private val httpClient = OkHttpClient()
        private val server = MockWebServer()

        val url: String
            get() = "http://127.0.0.1:${server.port}"

        fun start() {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path ?: return MockResponse().setResponseCode(HTTP_NOT_FOUND)
                    if (!path.contains(syntheticVersion)) {
                        return MockResponse().setResponseCode(HTTP_NOT_FOUND)
                    }

                    val upstreamPath = path.replace(syntheticVersion, realVersion)
                    val upstreamUrl = "$upstreamBaseUrl$upstreamPath"
                    val rewriter = selectRewriter(path) ?: return MockResponse()
                        .setResponseCode(HTTP_MOVED_TEMP)
                        .setHeader("Location", upstreamUrl)

                    return try {
                        val rewritten = rewriter(String(fetchFromUpstream(upstreamUrl)))
                        val base = MockResponse().setResponseCode(HTTP_OK)
                        if (request.method == "HEAD") {
                            base.setHeader("Content-Length", rewritten.toByteArray(Charsets.UTF_8).size.toString())
                        } else {
                            base.setBody(rewritten)
                        }
                    } catch (_: Exception) {
                        MockResponse().setResponseCode(HTTP_NOT_FOUND)
                    }
                }
            }
            server.start()
        }

        override fun close() {
            server.shutdown()
        }

        private fun selectRewriter(path: String): ((String) -> String)? {
            val isMarkerPom = path.contains("gradle.plugin") && path.endsWith(".pom")
            val isKgpPom = !path.contains("gradle.plugin") && path.endsWith(".pom")
            val isModule = path.endsWith(".module")

            return when {
                isMarkerPom -> { content ->
                    content.replace(realVersion, syntheticVersion)
                }
                isKgpPom -> { content ->
                    content.replaceFirst(
                        "<version>$realVersion</version>",
                        "<version>$syntheticVersion</version>"
                    )
                }
                isModule -> { content ->
                    content.replaceFirst(
                        "\"version\": \"$realVersion\"",
                        "\"version\": \"$syntheticVersion\""
                    )
                }
                else -> null
            }
        }

        private fun fetchFromUpstream(url: String): ByteArray {
            return httpClient.newCall(
                Request.Builder().url(url).build()
            ).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                response.body?.bytes() ?: throw Exception("Empty body")
            }
        }
    }
}
