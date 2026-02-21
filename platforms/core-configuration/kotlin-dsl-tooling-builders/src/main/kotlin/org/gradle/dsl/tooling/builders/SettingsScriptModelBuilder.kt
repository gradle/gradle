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

package org.gradle.dsl.tooling.builders

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.internal.build.BuildState
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.tooling.builders.compilationClassPathOf
import org.gradle.kotlin.dsl.tooling.builders.scriptImplicitImports
import org.gradle.tooling.model.buildscript.ScriptContextPathElement
import org.gradle.tooling.model.buildscript.SettingsScriptModel
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder
import java.io.File

object SettingsScriptModelBuilder : BuildScopeModelBuilder {

    override fun canBuild(modelName: String): Boolean =
        SettingsScriptModel::class.java.name == modelName

    override fun create(target: BuildState): SettingsScriptModel {
        target.ensureProjectsLoaded()
        val gradle = target.mutableModel
        val settings = gradle.settings
        val scriptFile = File(settings.settingsScript.fileName)

        return StandardSettingsScriptModel(
            StandardGradleScriptModel(
                scriptFile = scriptFile,
                implicitImports = gradle.scriptImplicitImports,
                contextPath = buildContextPathFor(scriptFile, gradle, settings),
            )
        )
    }

    private fun buildContextPathFor(
        scriptFile: File,
        gradle: GradleInternal,
        settings: SettingsInternal
    ): List<ScriptContextPathElement> =
        gradle.serviceOf<GradleScriptModelDependencies>().buildContextPathFor(
            scriptFile = scriptFile,
            classPathFiles = gradle.compilationClassPathOf(settings.classLoaderScope).asFiles,
            scriptHandlers = listOf(settings.buildscript)
        )
}
