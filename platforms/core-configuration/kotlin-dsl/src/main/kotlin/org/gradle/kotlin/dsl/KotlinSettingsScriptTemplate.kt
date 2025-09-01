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

import org.gradle.api.Incubating
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.plugins.PluginAware
import org.gradle.kotlin.dsl.support.DefaultKotlinScript
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.kotlin.dsl.support.defaultKotlinScriptHostForSettings
import org.gradle.plugin.use.PluginDependenciesSpec
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.filePathPattern
import kotlin.script.experimental.api.implicitReceivers


class KotlinSettingsScriptTemplateCompilationConfiguration : KotlinDslStandaloneScriptCompilationConfiguration({
    filePathPattern.put(".*/(?:.+\\.)?settings\\.gradle\\.kts")
    baseClass(KotlinSettingsScriptTemplate::class)
    implicitReceivers(Settings::class)
})


/**
 * Base class for Gradle Kotlin DSL standalone [Settings] scripts IDE support.
 *
 * This class has the [Incubating]-level compatibility guarantees but is not annotated as such to avoid Unstable API warnings caused by the IDE
 * using this class as the Kotlin DSL script template. When the IDE chooses to use this script template, there is no direct usage of an incubating
 * API by the build author, but usages of the members, such as [plugins], are still reported as unstable API usages.
 * See: [issue 34820](https://github.com/gradle/gradle/issues/34820).
 *
 * @since 8.1
 */
@KotlinScript(
    compilationConfiguration = KotlinSettingsScriptTemplateCompilationConfiguration::class
)
@GradleDsl
abstract class KotlinSettingsScriptTemplate(
    private val host: KotlinScriptHost<Settings>
) : DefaultKotlinScript(defaultKotlinScriptHostForSettings(host.target)), PluginAware by host.target {

    /**
     * The [ScriptHandler] for this script.
     */
    fun getBuildscript(): ScriptHandler =
        host.scriptHandler

    /**
     * Configures the plugin dependencies for the project's settings.
     *
     * @see [PluginDependenciesSpec]
     * @since 6.0
     */
    @Suppress("unused")
    open fun plugins(@Suppress("unused_parameter") block: PluginDependenciesSpecScope.() -> Unit): Unit =
        throw Exception(
            "The plugins {} block must not be used here. "
                + "If you need to apply a plugin imperatively, please use apply<PluginType>() or apply(plugin = \"id\") instead."
        )
}
