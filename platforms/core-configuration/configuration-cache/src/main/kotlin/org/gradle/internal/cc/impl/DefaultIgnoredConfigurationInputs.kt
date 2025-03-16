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

package org.gradle.internal.cc.impl

import org.gradle.internal.cc.impl.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.service.scopes.Scope.BuildTree
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.internal.GFileUtils
import java.io.File
import java.util.EnumSet
import javax.inject.Inject


@ServiceScope(BuildTree::class)
class DefaultIgnoredConfigurationInputs(
    ignoredPathsString: String?,
    isCaseSensitive: Boolean,
    private val rootDirectory: File
) : IgnoredConfigurationInputs {

    @Inject
    @Suppress("unused") // used in DI
    constructor(
        configurationCacheStartParameter: ConfigurationCacheStartParameter,
        fileSystem: FileSystem
    ) : this(
        ignoredPathsString = configurationCacheStartParameter.ignoredFileSystemCheckInputs,
        isCaseSensitive = fileSystem.isCaseSensitive,
        rootDirectory = configurationCacheStartParameter.rootDirectory
    )

    private
    val userHome = File(System.getProperty("user.home"))

    private
    val jointRegex: Regex? = maybeCreateJointRegexForPatterns(ignoredPathsString, isCaseSensitive)

    override fun isFileSystemCheckIgnoredFor(file: File): Boolean =
        jointRegex?.matches(normalizeActualInputPath(file)) ?: false

    private
    fun normalizeActualInputPath(file: File): String =
        file.let(::maybeRelativize)
            .let(File::normalize)
            .invariantSeparatorsPath

    private
    fun normalizeFilePattern(pathWithWildcards: String): String =
        File(pathWithWildcards)
            .let(::substituteUserHome)
            .let(::maybeRelativize)
            .let(File::normalize)
            .invariantSeparatorsPath

    private
    fun substituteUserHome(file: File): File =
        if (file.invariantSeparatorsPath.startsWith("~/"))
            File(userHome, file.path.substringAfter(File.separatorChar))
        else file

    private
    fun maybeRelativize(file: File): File =
        if (!file.isAbsolute) file else File(GFileUtils.relativePathOf(file, rootDirectory))

    private
    fun wildcardsToRegexPatternString(pathWithWildcards: String): String {
        fun String.runIfNotEmpty(action: String.() -> String): String =
            if (this.isEmpty()) this else action()

        return pathWithWildcards.split("**").joinToString(separator = ".*", prefix = "^", postfix = "$") { outerPart ->
            outerPart.runIfNotEmpty {
                split("*").joinToString("[^/]*") { innerPart ->
                    innerPart.runIfNotEmpty(Regex::escape)
                }
            }
        }
    }

    private
    fun maybeCreateJointRegexForPatterns(paths: String?, isCaseSensitive: Boolean) =
        if (paths.isNullOrEmpty()) {
            null
        } else {
            paths.split(PATHS_SEPARATOR).joinToString("|") {
                wildcardsToRegexPatternString(normalizeFilePattern(it))
            }.toRegex(if (!isCaseSensitive) EnumSet.of(RegexOption.IGNORE_CASE) else emptySet())
        }

    private
    companion object {
        const val PATHS_SEPARATOR = ";"
    }
}
