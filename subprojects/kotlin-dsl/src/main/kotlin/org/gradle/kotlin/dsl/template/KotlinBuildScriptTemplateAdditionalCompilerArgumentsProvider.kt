/*
 * Copyright 2022 the original author or authors.
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

// We implemented deprecated ScriptTemplateAdditionalCompilerArgumentsProvider since there is no good replacement yet.
@file:Suppress("DEPRECATION")

package org.gradle.kotlin.dsl.template

import org.gradle.kotlin.dsl.precompile.PrecompiledScriptDependenciesResolver.EnvironmentProperties
import java.io.File
import java.io.FileInputStream
import java.util.Properties
import kotlin.script.dependencies.Environment
import kotlin.script.templates.ScriptTemplateAdditionalCompilerArgumentsProvider


class KotlinBuildScriptTemplateAdditionalCompilerArgumentsProvider(additionalArguments: Iterable<String> = emptyList()) :
    ScriptTemplateAdditionalCompilerArgumentsProvider(additionalArguments) {

    override fun getAdditionalCompilerArguments(environment: Environment?): Iterable<String> {
        return if (environment.isKotlinDslAssignmentExplicitlyDisabled()) {
            emptyList()
        } else {
            // This class is loaded from the IDE, so we have to hardcode SupportsKotlinAssignmentOverloading name,
            // since SupportsKotlinAssignmentOverloading doesn't exist on the IDE classpath
            listOf("-P=plugin:org.jetbrains.kotlin.assignment:annotation=org.gradle.api.SupportsKotlinAssignmentOverloading")
        }
    }

    private
    fun Environment?.isKotlinDslAssignmentExplicitlyDisabled(): Boolean {
        this ?: return false
        val projectRoot = this[EnvironmentProperties.projectRoot] as? File ?: return false
        val gradleProperties = File(projectRoot, "gradle.properties")
        if (!gradleProperties.exists()) return false
        return FileInputStream(gradleProperties).use {
            val properties = Properties().apply {
                load(it)
            }
            // This class is loaded from the IDE, so we have to hardcode system property
            properties.getProperty("systemProp.org.gradle.unsafe.kotlin.assignment", "true").trim() == "false"
        }
    }
}
