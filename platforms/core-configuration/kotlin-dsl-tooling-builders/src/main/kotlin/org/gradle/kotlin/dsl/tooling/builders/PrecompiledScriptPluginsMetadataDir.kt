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

package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.api.Project
import org.gradle.kotlin.dsl.provider.PrecompiledScriptsEnvironment.EnvironmentProperties.kotlinDslPluginSpecBuildersImplicitImports
import org.gradle.kotlin.dsl.support.KotlinScriptHashing
import java.io.File

internal class PrecompiledScriptPluginsMetadataDir(private val dir: File) {

    companion object {
        fun of(project: Project): PrecompiledScriptPluginsMetadataDir {
            val dir = project.layout.buildDirectory
                .dir("kotlin-dsl/precompiled-script-plugins-metadata")
                .get()
                .asFile
            return PrecompiledScriptPluginsMetadataDir(dir)
        }
    }

    val implicitPluginSpecBuildersImports: List<String>
        get() = with(dir) {
            implicitImportsFrom(
                resolve("plugin-spec-builders").resolve(kotlinDslPluginSpecBuildersImplicitImports)
            ) + implicitImportsFrom(
                // Gradle <= 8.12 was using this other name with a dash but this was incompatible with moving to kotlin-scripting-host API
                // Keeping it for compatibility with previous Gradle versions
                resolve("plugin-spec-builders").resolve("implicit-imports")
            )
        }

    fun implicitAccessorsImports(scriptFile: File): List<String> = with(dir) {
        implicitImportsFrom(resolve("accessors").resolve(hashOf(scriptFile)))
    }

    private
    fun implicitImportsFrom(file: File): List<String> =
        file.takeIf { it.isFile }?.readLines() ?: emptyList()

    private
    fun hashOf(scriptFile: File) =
        KotlinScriptHashing.hashOf(scriptFile.readText())
}
