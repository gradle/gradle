/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependency
import org.gradle.plugin.use.PluginDependencySpec


/**
 * Receiver for the `plugins` block.
 *
 * This class exists for the sole purpose of marking the `plugins` block as a [GradleDsl] thus
 * hiding all members provided by the outer [KotlinBuildScript] scope.
 *
 * @see [PluginDependenciesSpec]
 */
@GradleDsl
class PluginDependenciesSpecScope internal constructor(
    private val plugins: PluginDependenciesSpec
) : PluginDependenciesSpec {

    override fun id(id: String): PluginDependencySpec =
        plugins.id(id)

    override fun alias(notation: Provider<PluginDependency>) =
        plugins.alias(notation)

    override fun alias(notation: ProviderConvertible<PluginDependency>) =
        plugins.alias(notation)
}


/**
 * Specify the version of the plugin to depend on.
 *
 * Infix version of [PluginDependencySpec.version].
 */
infix fun PluginDependencySpec.version(version: String?): PluginDependencySpec = version(version)


/**
 * Specify the version of the plugin to depend on.
 *
 * Infix version of [PluginDependencySpec.version].
 *
 * @since 7.2
 */
@Incubating
infix fun PluginDependencySpec.version(version: Provider<String>): PluginDependencySpec = version(version)


/**
 * Specifies whether the plugin should be applied to the current project. Otherwise it is only put
 * on the project's classpath.
 *
 * Infix version of [PluginDependencySpec.apply].
 */
infix fun PluginDependencySpec.apply(apply: Boolean): PluginDependencySpec = apply(apply)
