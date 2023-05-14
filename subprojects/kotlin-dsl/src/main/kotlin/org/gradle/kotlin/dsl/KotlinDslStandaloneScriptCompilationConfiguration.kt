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

package org.gradle.kotlin.dsl

import org.gradle.api.HasImplicitReceiver
import org.jetbrains.kotlin.scripting.definitions.annotationsForSamWithReceivers
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.isStandalone


/**
 * Common script compilation configuration for Kotlin DSL standalone scripts.
 */
internal
abstract class KotlinDslStandaloneScriptCompilationConfiguration protected constructor(
    body: Builder.() -> Unit
) : ScriptCompilationConfiguration({

    isStandalone(true)
    compilerOptions.put(listOf(
        "-language-version", "1.8",
        "-api-version", "1.8",
        "-Xjvm-default=all",
        "-Xjsr305=strict",
        "-XXLanguage:+DisableCompatibilityModeForNewInference",
        "-XXLanguage:-TypeEnhancementImprovementsInStrictMode",
    ))
    annotationsForSamWithReceivers.put(listOf(
        KotlinType(HasImplicitReceiver::class),
    ))

    body()
})
