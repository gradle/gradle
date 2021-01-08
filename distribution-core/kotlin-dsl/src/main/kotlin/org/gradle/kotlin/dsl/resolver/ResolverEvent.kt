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

package org.gradle.kotlin.dsl.resolver

import kotlin.script.dependencies.KotlinScriptExternalDependencies

import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel

import com.google.common.annotations.VisibleForTesting

import java.io.File


@VisibleForTesting
sealed class ResolverEvent


@VisibleForTesting
data class ResolutionRequest internal constructor(
    val correlationId: String,
    val scriptFile: File?,
    val environment: Map<String, Any?>?,
    val previousDependencies: KotlinScriptExternalDependencies?
) : ResolverEvent()


@VisibleForTesting
data class ResolutionFailure internal constructor(
    val correlationId: String,
    val scriptFile: File?,
    val failure: Exception
) : ResolverEvent()


@VisibleForTesting
data class SubmittedModelRequest internal constructor(
    val correlationId: String,
    val scriptFile: File?,
    val request: KotlinBuildScriptModelRequest
) : ResolverEvent()


internal
data class RequestCancelled(
    val correlationId: String,
    val scriptFile: File?,
    val request: KotlinBuildScriptModelRequest
) : ResolverEvent()


@VisibleForTesting
data class ReceivedModelResponse internal constructor(
    val correlationId: String,
    val scriptFile: File?,
    val response: KotlinBuildScriptModel
) : ResolverEvent()


@VisibleForTesting
data class ResolvedDependencies internal constructor(
    val correlationId: String,
    val scriptFile: File?,
    val dependencies: KotlinScriptExternalDependencies
) : ResolverEvent()


@VisibleForTesting
data class ResolvedDependenciesWithErrors internal constructor(
    val correlationId: String,
    val scriptFile: File?,
    val dependencies: KotlinScriptExternalDependencies,
    val exceptions: List<String>
) : ResolverEvent()


internal
data class ResolvedToPrevious(
    val correlationId: String,
    val scriptFile: File?,
    val dependencies: KotlinScriptExternalDependencies?
) : ResolverEvent()


internal
data class ResolvedToPreviousWithErrors(
    val correlationId: String,
    val scriptFile: File?,
    val dependencies: KotlinScriptExternalDependencies,
    val exceptions: List<String>
) : ResolverEvent()
