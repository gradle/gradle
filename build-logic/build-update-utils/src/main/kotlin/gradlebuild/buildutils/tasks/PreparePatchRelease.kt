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

package gradlebuild.buildutils.tasks

import com.google.gson.Gson
import gradlebuild.buildutils.model.ReleasedVersion
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse


private
data class GradleServicesVersion(val version: String, val buildTime: String)


@DisableCachingByDefault(because = "Not worth caching")
abstract class PreparePatchRelease : DefaultTask() {

    @get:Internal
    abstract val versionFile: RegularFileProperty

    @get:Internal
    abstract val releasedVersionsFile: RegularFileProperty

    @TaskAction
    fun prepare() {
        val currentVersion = versionFile.asFile.get().readText().trim()
        val patchVersion = bumpPatchVersion(currentVersion)
        val major = currentVersion.split(".")[0].toInt()

        val previousReleasedVersion = fetchVersionFromGradleServices(major, currentVersion)
        versionFile.asFile.get().writeText(patchVersion)
        updateReleasedVersionFile(releasedVersionsFile.asFile.get(), previousReleasedVersion)
        println("Updated version.txt: $currentVersion -> $patchVersion")
        println("Updated released-versions.json with $currentVersion (buildTime: ${previousReleasedVersion.buildTime})")
    }

    private
    fun fetchVersionFromGradleServices(major: Int, targetVersion: String): ReleasedVersion {
        val uri = "https://services.gradle.org/versions/$major"
        val request = HttpRequest.newBuilder().uri(URI(uri)).build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() > 399) {
            throw RuntimeException("Failed to fetch versions from Gradle services: ${response.statusCode()} ${response.body()}")
        }
        val versions = Gson().fromJson(response.body(), Array<GradleServicesVersion>::class.java)
        val found = versions.find { it.version == targetVersion }
            ?: throw RuntimeException("Version $targetVersion not found at $uri")
        return ReleasedVersion(found.version, found.buildTime)
    }
}
