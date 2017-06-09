/*
 * Copyright 2017 the original author or authors.
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

import kotlin.script.dependencies.KotlinScriptExternalDependencies

import org.gradle.script.lang.kotlin.tooling.models.KotlinBuildScriptModel

import java.io.File


internal
sealed class ResolverEvent


internal
data class ResolutionFailure(
    val scriptFile: File?,
    val failure: Exception) : ResolverEvent()


internal
data class ResolutionProgress(
    val scriptFile: File?,
    val description: String) : ResolverEvent()


internal
data class ResolvedToPrevious(
    val scriptFile: File?,
    val environment: Map<String, Any?>?,
    val previousDependencies: KotlinScriptExternalDependencies?) : ResolverEvent()


internal
data class SubmittedModelRequest(
    val scriptFile: File?,
    val request: KotlinBuildScriptModelRequest) : ResolverEvent()


internal
data class ReceivedModelResponse(
    val scriptFile: File?,
    val response: KotlinBuildScriptModel) : ResolverEvent()


internal
data class ResolvedDependencies(
    val scriptFile: File?,
    val dependencies: KotlinScriptExternalDependencies) : ResolverEvent()
