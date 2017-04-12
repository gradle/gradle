/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.script.lang.kotlin.resolver

import java.io.File

internal
object SourcePathProvider {

    fun sourcePathFor(
        request: KotlinBuildScriptModelRequest,
        response: KotlinBuildScriptModel): Collection<File> {

        val gradleScriptKotlinJar = response.classPath.filter { it.name.startsWith("gradle-script-kotlin-") }
        val projectBuildSrcRoots = buildSrcRootsOf(request.projectDir)
        val gradleInstallation = request.gradleInstallation
        val gradleSourceRoots =
            (gradleInstallation as? GradleInstallation.Local)?.run { sourceRootsOf(dir) } ?: emptyList()
        return gradleScriptKotlinJar + projectBuildSrcRoots + gradleSourceRoots
    }

    /**
     * Returns all conventional source directories under buildSrc if any.
     *
     * This won't work for buildSrc projects with a custom source directory layout
     * but should account for the majority of cases and it's cheap.
     */
    private fun buildSrcRootsOf(projectRoot: File): Collection<File> =
        subDirsOf(File(projectRoot, "buildSrc/src/main"))

    private fun sourceRootsOf(gradleInstallation: File): Collection<File> =
        subDirsOf(File(gradleInstallation, "src"))

    private fun subDirsOf(dir: File): Collection<File> =
        if (dir.isDirectory)
            dir.listFiles().filter { it.isDirectory }
        else
            emptyList()
}
