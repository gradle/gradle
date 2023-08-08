/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.support

import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.ProcessOperations
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.logging.LoggingManager
import org.gradle.api.plugins.PluginAware


@ImplicitReceiver(Settings::class)
open class CompiledKotlinSettingsScript(
    private val host: KotlinScriptHost<Settings>
) : DefaultKotlinScript(SettingsScriptHost(host)), PluginAware by PluginAwareScript(host) {

    /**
     * The [ScriptHandler] for this script.
     */
    val buildscript: ScriptHandler
        get() = host.scriptHandler

    private
    class SettingsScriptHost(val host: KotlinScriptHost<Settings>) : Host {
        override fun getLogger(): Logger = Logging.getLogger(Settings::class.java)
        override fun getLogging(): LoggingManager = host.target.serviceOf()
        override fun getFileOperations(): FileOperations = host.fileOperations
        override fun getProcessOperations(): ProcessOperations = host.processOperations
    }
}
