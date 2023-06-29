/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.service.scopes.Scopes.BuildTree
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.internal.GFileUtils
import java.io.File
import javax.inject.Inject


@ServiceScope(BuildTree::class)
class DefaultIgnoredConfigurationInputs(
    ignoredPathsString: String,
    private val rootDirectory: File
) : IgnoredConfigurationInputs {

    @Inject
    @Suppress("unused") // used in DI
    constructor(configurationCacheStartParameter: ConfigurationCacheStartParameter) :
        this(configurationCacheStartParameter.ignoredFileSystemCheckInputs.orEmpty(), configurationCacheStartParameter.rootDirectory)

    private
    val userHome = File(System.getProperty("user.home"))

    private
    val jointRegex = createJointRegexForPatterns(ignoredPathsString)

    override fun isFileSystemCheckIgnoredFor(file: File): Boolean =
        jointRegex.matches(normalizeActualInputPath(file))

    private
    fun normalizeActualInputPath(file: File): String =
        file.let(::relativize)
            .let(File::normalize)
            .invariantSeparatorsPath

    private
    fun normalizeFilePattern(pathWithWildcards: String): File =
        File(pathWithWildcards)
            .let(::substituteUserHome)
            .let(::relativize)
            .let(File::normalize)

    private
    fun substituteUserHome(file: File): File =
        if (file.invariantSeparatorsPath.startsWith("~/"))
            File(userHome, file.path.substringAfter(File.separatorChar))
        else file

    private
    fun relativize(file: File): File {
        val absoluteFile = if (file.isAbsolute) file else File(rootDirectory, file.path)
        return File(GFileUtils.relativePathOf(absoluteFile, rootDirectory))
    }

    private
    fun wildcardsToRegexPatternString(pathWithWildcards: String): String =
        pathWithWildcards.split("**").joinToString(separator = ".*", prefix = "^", postfix = "$") { outerPart ->
            outerPart.takeIf { it.isNotEmpty() }?.split("*")?.joinToString("[^/]*") { innerPart ->
                innerPart.takeIf { it.isNotEmpty() }?.let(Regex::escape).orEmpty()
            }.orEmpty()
        }

    private
    fun createJointRegexForPatterns(paths: String) =
        paths.split(PATHS_SEPARATOR).joinToString("|") {
            wildcardsToRegexPatternString(normalizeFilePattern(it).invariantSeparatorsPath)
        }.toRegex()

    internal
    companion object {
        internal
        const val PATHS_SEPARATOR = ";"
    }
}
