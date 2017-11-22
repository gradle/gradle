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
import org.gradle.kotlin.dsl.support.filter
import java.io.File


object SourcePathProvider {

    fun sourcePathFor(
        classPath: ClassPath,
        projectDir: File,
        gradleHomeDir: File?,
        sourceDistributionResolver: SourceDistributionResolver): ClassPath {

        val gradleKotlinDslJar = classPath.filter { it.name.startsWith("gradle-kotlin-dsl-") }
        val projectBuildSrcRoots = buildSrcRootsOf(projectDir)
        val gradleSourceRoots = gradleHomeDir?.let { sourceRootsOf(it, sourceDistributionResolver) } ?: emptyList()

        return gradleKotlinDslJar + projectBuildSrcRoots + gradleSourceRoots
    }

    /**
     * Returns all conventional source directories under buildSrc if any.
     *
     * This won't work for buildSrc projects with a custom source directory layout
     * but should account for the majority of cases and it's cheap.
     */
    private
    fun buildSrcRootsOf(projectRoot: File): Collection<File> =
        subDirsOf(File(projectRoot, "buildSrc/src/main"))

    private
    fun sourceRootsOf(gradleInstallation: File, sourceDistributionResolver: SourceDistributionResolver): Collection<File> {
        val dir = File(gradleInstallation, "src")
        if (dir.exists()) {
            return subDirsOf(dir)
        }
        return sourceDistributionResolver.downloadAndResolveSources()
    }

    private
    fun subDirsOf(dir: File): Collection<File> =
        if (dir.isDirectory)
            dir.listFiles().filter { it.isDirectory }
        else
            emptyList()
}
