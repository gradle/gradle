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

import org.gradle.plugin.dsl.PluginDependenciesSpec
import org.gradle.plugin.dsl.BinaryPluginDependencySpec


/**
 * Receiver for the `plugins` block.
 *
 * This class exists for the sole purpose of marking the `plugins` block as a [GradleDsl] thus
 * hiding all members provided by the outer [KotlinBuildScript] scope.
 *
 * @see PluginDependenciesSpec
 */
@GradleDsl
class PluginDependenciesSpecScope(plugins: PluginDependenciesSpec) : PluginDependenciesSpec by plugins


/**
 * Specify the version of the plugin to depend on.
 *
 * Infix version of [BinaryPluginDependencySpec.version].
 */
infix fun BinaryPluginDependencySpec.version(version: String?): BinaryPluginDependencySpec = version(version)


/**
 * Specifies whether the plugin should be applied to the current project. Otherwise it is only put
 * on the project's classpath.
 *
 * Infix version of [PluginDependencySpec.apply].
 */
infix fun BinaryPluginDependencySpec.apply(apply: Boolean): BinaryPluginDependencySpec = apply(apply)
