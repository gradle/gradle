/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.ntcscript

import org.gradle.ntcscript.BuildScriptModel.Dependency
import org.gradle.ntcscript.BuildScriptModel.Dependency.ExternalDependency
import org.gradle.ntcscript.BuildScriptModel.ElementPosition
import org.gradle.ntcscript.BuildScriptModel.Extension
import org.gradle.ntcscript.BuildScriptModel.Plugin
import org.gradle.ntcscript.BuildScriptModel.PropertyNode.DoubleProperty
import org.gradle.ntcscript.BuildScriptModel.PropertyNode.IntegerProperty
import org.gradle.ntcscript.BuildScriptModel.PropertyNode.StringProperty
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable


class NtcScriptReader {

    companion object {
        val RESERVED_NODES = listOf(
            "project",
            "plugins",
            "repositories",
            "dependencies"
        )
    }

    fun readToml(toml: String): BuildScriptModel {
        val result: TomlParseResult = Toml.parse(toml)
        val plugins = readPlugins(result)
        val extensions = readExtensions(result)
        val dependencies = readDependencies(result)
        return BuildScriptModel(
            plugins = plugins,
            extensions = extensions,
            dependencies = dependencies
        )
    }

    private
    fun readPlugins(result: TomlParseResult): List<Plugin> {
        val pluginsTable = result.getTable("plugins")
        val plugins = pluginsTable?.toMap()?.map {
            val position: ElementPosition = pluginsTable.readPositionOf(it.key)
            (it.value as? TomlTable)?.readPlugin(it.key, position)
        }?.filterNotNull()?.toList()
        return plugins ?: emptyList()
    }

    private
    fun TomlTable.readPlugin(id: String, position: ElementPosition): Plugin {
        val version = getString("version")
        return Plugin(id, position, version)
    }

    private
    fun readDependencies(result: TomlParseResult): Map<String, List<Dependency>> {
        val dependenciesTable = result.getTable("dependencies")
        val dependencies = dependenciesTable?.toMap()?.mapValues {
            (it.value as? TomlTable)?.readConfiguration() ?: emptyList()
        }?.filterValues { it.isNotEmpty() }
        return dependencies ?: mapOf()
    }

    private
    fun TomlTable.readConfiguration(): List<Dependency> {
        return toMap()?.flatMap {
            (it.value as? TomlTable)?.readDependency(it.key) ?: emptyList()
        } ?: emptyList()
    }

    private
    fun TomlTable.readDependency(group: String): List<Dependency> {
        return keySet().filter {
            get(it) !is String && getTable(it) != null
        }.map {
            val table = getTable(it)!!
            val version = table.getString("version")
            val isPlatform = table.getBoolean("platform") { false }
            when {
                group == "project" -> Dependency.ProjectDependency(it)
                isPlatform -> Dependency.PlatformDependency(group, it, version)
                else -> ExternalDependency(group, it, version)
            }
        }.toList() + keySet().filter {
            get(it) is String
        }.map {
            val version = getString(it)
            ExternalDependency(group, it, version)
        }
    }

    private
    fun TomlTable.readPositionOf(key: String): ElementPosition {
        val tomlPosition = inputPositionOf("\"${key}\"")
        return when (tomlPosition) {
            null -> ElementPosition(-1, -1)
            else -> ElementPosition(tomlPosition.line(), tomlPosition.column())
        }
    }

    private
    fun readExtensions(result: TomlParseResult): List<Extension> {
        val extensions = result.toMap()?.filter {
            it.key !in RESERVED_NODES
        }?.map {
            (it.value as? TomlTable)?.readExtension(it.key)
        }?.filterNotNull()?.toList()
        return extensions ?: emptyList()
    }

    private
    fun TomlTable.readExtension(name: String): Extension {
        val properties = toMap().mapValues {
            when (it.value) {
                is Long -> IntegerProperty((it.value as Long).toInt())
                is Int -> IntegerProperty(it.value as Int)
                is Double -> DoubleProperty(it.value as Double)
                is String -> StringProperty(it.value as String)
                else -> throw UnsupportedOperationException("Parsing of nested ")
            }
        }
        return Extension(name, properties = properties)
    }
}
