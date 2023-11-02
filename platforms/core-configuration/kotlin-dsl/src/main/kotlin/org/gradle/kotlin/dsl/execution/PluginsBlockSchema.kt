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

package org.gradle.kotlin.dsl.execution

import com.h0tk3y.kotlin.staticObjectNotation.Adding
import com.h0tk3y.kotlin.staticObjectNotation.Builder
import com.h0tk3y.kotlin.staticObjectNotation.Configuring
import com.h0tk3y.kotlin.staticObjectNotation.Restricted
import com.h0tk3y.kotlin.staticObjectNotation.schemaFromTypes
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec


internal
abstract class TopLevelReceiver {
    @Restricted
    abstract val plugins: RestrictedPluginDependenciesSpecScope

    @Configuring
    fun plugins(configure: RestrictedPluginDependenciesSpecScope.() -> Unit) {
        plugins.configure()
    }
}


internal
abstract class RestrictedPluginDependenciesSpecScope : PluginDependenciesSpec {
    @Adding
    abstract override fun id(id: String): RestrictedPluginDependencySpec

    @Adding
    abstract fun kotlin(id: String): KotlinRestrictedPluginDependencySpec
}


abstract class KotlinRestrictedPluginDependencySpec : RestrictedPluginDependencySpec()


abstract class RestrictedPluginDependencySpec : PluginDependencySpec {
    @Restricted
    abstract val id: String

    @Restricted
    abstract val version: String?

    @Restricted
    abstract val apply: Boolean

    @Builder
    abstract override fun apply(apply: Boolean): RestrictedPluginDependencySpec

    @Builder
    abstract override fun version(version: String?): RestrictedPluginDependencySpec
}


internal
val pluginsBlockAnalysisSchema = schemaFromTypes(
    TopLevelReceiver::class,
    listOf(TopLevelReceiver::class, RestrictedPluginDependenciesSpecScope::class, RestrictedPluginDependencySpec::class, KotlinRestrictedPluginDependencySpec::class)
)
