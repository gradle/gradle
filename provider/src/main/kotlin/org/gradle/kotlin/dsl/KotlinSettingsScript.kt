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
package org.gradle.kotlin.dsl

import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver
import kotlin.script.extensions.SamWithReceiverAnnotations
import kotlin.script.templates.ScriptTemplateDefinition

/**
 * Base class for Kotlin settings scripts.
 */
@ScriptTemplateDefinition(
    resolver = KotlinBuildScriptDependenciesResolver::class,
    scriptFilePattern = "settings\\.gradle\\.kts")
@SamWithReceiverAnnotations("org.gradle.api.HasImplicitReceiver")
abstract class KotlinSettingsScript(settings: Settings) : Settings by settings {

    inline
    fun apply(crossinline configuration: ObjectConfigurationAction.() -> Unit) =
        settings.apply({ it.configuration() })
}
