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

package org.gradle.internal.declarativedsl.plugins

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

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Builder
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition


class PluginsTopLevelReceiver {
    val plugins = PluginsCollectingPluginsBlock()
}


class PluginsCollectingPluginsBlock {
    @get:HiddenInDefinition
    val specs: List<MutablePluginDependencySpec>
        get() = _specs

    private
    val _specs = mutableListOf<MutablePluginDependencySpec>()

    @Adding
    fun id(id: String): MutablePluginDependencySpec = MutablePluginDependencySpec(id).also(_specs::add)

    @Adding
    fun kotlin(id: String) = id("org.jetbrains.kotlin.$id")
}


class MutablePluginDependencySpec(
    @get:HiddenInDefinition
    val id: String
) {
    @get:HiddenInDefinition
    var versionIsSet = false
        private set

    var version: String = ""
        private set(version) {
            field = version
            versionIsSet = true
        }

    var apply: Boolean = true
        private
        set

    @Builder
    fun version(version: String): MutablePluginDependencySpec {
        this.version = version
        return this
    }

    @Builder
    fun apply(apply: Boolean): MutablePluginDependencySpec {
        this.apply = apply
        return this
    }
}
