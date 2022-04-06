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

package org.gradle.kotlin.dsl.resolver

import org.gradle.internal.classpath.ClassPath
import org.gradle.kotlin.dsl.support.KotlinScriptType
import org.gradle.kotlin.dsl.support.KotlinScriptTypeMatch

import org.gradle.kotlin.dsl.support.filter
import org.gradle.kotlin.dsl.support.isGradleKotlinDslJar
import org.gradle.kotlin.dsl.support.listFilesOrdered

import java.io.File


const val buildSrcSourceRootsFilePath = "build/source-roots/buildSrc/source-roots.txt"


object SourcePathProvider {

    fun sourcePathFor(
        classPath: ClassPath,
        scriptFile: File?,
        projectDir: File,
        gradleHomeDir: File?,
        sourceDistributionResolver: SourceDistributionProvider
    ): ClassPath {
        val gradleKotlinDslJar = classPath.filter(::isGradleKotlinDslJar)
        val gradleSourceRoots = gradleHomeDir?.let { sourceRootsOf(it, sourceDistributionResolver) } ?: emptyList()

        // If the script file is known, determine its type
        val scriptType = scriptFile?.let {
            KotlinScriptTypeMatch.forFile(it)?.scriptType
        }
        // We also add the "buildSrc" sources onto the source path.
        // Only exception is the "settings.gradle.kts" script, which is evaluated before "buildSrc", so it shouldn't see the sources
        val projectBuildSrcRoots = when (scriptType) {
            KotlinScriptType.SETTINGS -> emptyList()
            else -> buildSrcRootsOf(projectDir)
        }

        return gradleKotlinDslJar + projectBuildSrcRoots + gradleSourceRoots
    }

    /**
     * Returns source directories from buildSrc if any.
     */
    private
    fun buildSrcRootsOf(projectRoot: File): Collection<File> =
        projectRoot.resolve("buildSrc/$buildSrcSourceRootsFilePath")
            .takeIf { it.isFile }
            ?.readLines()
            ?.map { projectRoot.resolve("buildSrc/$it") }
            ?: buildSrcRootsFallbackFor(projectRoot)

    private
    fun buildSrcRootsFallbackFor(projectRoot: File) =
        subDirsOf(File(projectRoot, "buildSrc/src/main"))

    private
    fun sourceRootsOf(gradleInstallation: File, sourceDistributionResolver: SourceDistributionProvider): Collection<File> =
        gradleInstallationSources(gradleInstallation) ?: downloadedSources(sourceDistributionResolver)

    private
    fun gradleInstallationSources(gradleInstallation: File) =
        File(gradleInstallation, "src").takeIf { it.exists() }?.let { subDirsOf(it) }

    private
    fun downloadedSources(sourceDistributionResolver: SourceDistributionProvider) =
        sourceDistributionResolver.sourceDirs()
}


internal
fun subDirsOf(dir: File): Collection<File> =
    if (dir.isDirectory) dir.listFilesOrdered { it.isDirectory }
    else emptyList()
