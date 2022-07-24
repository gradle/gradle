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

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.plugins.PluginAware
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.plugins.PluginManager


open class PluginAwareScript(
    private val host: KotlinScriptHost<PluginAware>
) : PluginAware {

    override fun getPlugins(): PluginContainer =
        host.target.plugins

    override fun getPluginManager(): PluginManager =
        host.target.pluginManager

    override fun apply(action: Action<in ObjectConfigurationAction>) =
        host.applyObjectConfigurationAction(action)

    override fun apply(options: Map<String, *>) =
        host.applyObjectConfigurationAction(options)

    override fun apply(closure: Closure<Any>) =
        internalError()
}
