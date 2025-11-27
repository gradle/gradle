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

package gradlebuild.integrationtests.androidhomewarmup

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * Plugin that pre-downloads Android SDK components to prevent race conditions during parallel builds.
 *
 * When multiple Android builds run in parallel and share the same `ANDROID_HOME`, the Android Gradle Plugin
 * may attempt to download and install SDK components (platforms, build-tools, platform-tools) simultaneously.
 * This can cause race conditions leading to:
 * - `FileAlreadyExistsException` when extracting files
 * - Corrupted installations (e.g., "Build-tool X has corrupt source.properties")
 * - Missing SDK components after installation
 *
 * This plugin generates minimal Android projects for specified SDK version combinations and builds them
 * sequentially before parallel builds run, ensuring all required SDK components are already downloaded.
 *
 * @see <a href="https://github.com/gradle/gradle-private/issues/4910">Issue #4910</a>
 */
class AndroidHomeWarmupPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension =
            project.extensions.create("androidHomeWarmup", AndroidHomeWarmupExtension::class.java).apply {
                warmupProjectsDirectory.convention(project.layout.buildDirectory.dir("android-home-warmup"))
            }

        project.tasks.register("androidHomeWarmup", AndroidHomeWarmupTask::class) {
            warmupProjectsDirectory.set(extension.warmupProjectsDirectory)
            sdkVersions.set(extension.sdkVersions)
            rootProjectDir.set(extension.rootProjectDir)
        }
    }
}

