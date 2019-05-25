/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.accessors


internal
sealed class PluginTree {

    data class PluginGroup(val path: List<String>, val plugins: Map<String, PluginTree>) : PluginTree()

    data class PluginSpec(val id: String, val implementationClass: String) : PluginTree()

    companion object {

        fun of(plugins: Sequence<PluginSpec>): Map<String, PluginTree> {
            val root = linkedMapOf<String, PluginTree>()
            plugins.sortedBy { it.id }.forEach { plugin ->
                val path = plugin.id.split('.')
                val pluginGroupPath = path.dropLast(1)
                pluginTreeForGroup(pluginGroupPath, root)
                    ?.put(path.last(), plugin)
            }
            return root
        }

        private
        fun pluginTreeForGroup(groupPath: List<String>, root: MutableMap<String, PluginTree>): MutableMap<String, PluginTree>? {
            var branch = root
            groupPath.forEachIndexed { index, segment ->
                when (val group = branch[segment]) {
                    null -> {
                        val newGroupMap = linkedMapOf<String, PluginTree>()
                        val newGroup = PluginGroup(groupPath.take(index + 1), newGroupMap)
                        branch[segment] = newGroup
                        branch = newGroupMap
                    }
                    is PluginGroup -> {
                        branch = group.plugins as MutableMap<String, PluginTree>
                    }
                    else -> {
                        return null
                    }
                }
            }
            return branch
        }
    }
}
