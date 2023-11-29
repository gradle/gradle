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

package org.gradle.internal.restricteddsl.plugins

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

import com.h0tk3y.kotlin.staticObjectNotation.Adding
import com.h0tk3y.kotlin.staticObjectNotation.Builder
import com.h0tk3y.kotlin.staticObjectNotation.Configuring
import com.h0tk3y.kotlin.staticObjectNotation.Restricted
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec


internal
abstract class PluginsTopLevelReceiver {
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
    abstract override fun id(id: String): PluginDependencySpecWithProperties

    @Adding
    abstract fun kotlin(id: String): PluginDependencySpecWithProperties
}


abstract class PluginDependencySpecWithProperties : PluginDependencySpec {
    @Restricted
    abstract val version: String?

    @Restricted
    abstract val apply: Boolean

    @Builder
    abstract override fun version(version: String?): PluginDependencySpecWithProperties

    @Builder
    abstract override fun apply(apply: Boolean): PluginDependencySpecWithProperties
}


class RuntimeTopLevelPluginsReceiver {
    val plugins = PluginsCollectingPluginsBlock()
}


class PluginsCollectingPluginsBlock() : PluginDependenciesSpec {
    val specs: List<MutablePluginDependencySpec>
        get() = _specs

    private
    val _specs = mutableListOf<MutablePluginDependencySpec>()

    override fun id(id: String): PluginDependencySpec =
        MutablePluginDependencySpec(id)
            .also(_specs::add)

    fun kotlin(id: String) =
        id("org.jetbrains.kotlin.$id")
}


class MutablePluginDependencySpec(
    val id: String
) : PluginDependencySpec {
    var version: String? = null
        private
        set

    var apply: Boolean = true
        private
        set

    override fun version(version: String?): PluginDependencySpec {
        this.version = version
        return this
    }

    override fun apply(apply: Boolean): PluginDependencySpec {
        this.apply = apply
        return this
    }
}
