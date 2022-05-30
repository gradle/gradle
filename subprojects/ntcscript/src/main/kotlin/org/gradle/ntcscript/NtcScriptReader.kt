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
import org.gradle.ntcscript.BuildScriptModel.PropertyValue.DoubleValue
import org.gradle.ntcscript.BuildScriptModel.PropertyValue.IntValue
import org.gradle.ntcscript.BuildScriptModel.PropertyValue.StringValue
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable
import java.util.Collections.singletonList


class NtcScriptReader {

    companion object {
        /**
         * Builtin configuration nodes, everything else is considered an extension.
         */
        private
        val BUILTIN_NODES = setOf(
            // builtin project properties
            "description",
            "version",
            // builtin collections
            "plugins",
            "repositories",
            "dependencies"
        )
    }

    fun readToml(toml: String): BuildScriptModel =
        Toml.parse(toml).run {
            BuildScriptModel(
                plugins = readPlugins(),
                extensions = readExtensions(),
                dependencies = readDependencies()
            )
        }

    private
    fun TomlParseResult.readPlugins(): List<Plugin> =
        getTable("plugins")?.run {
            keySet().mapNotNull { pluginId ->
                singletonList(pluginId).let { pluginPath ->
                    getTable(pluginPath)?.readPlugin(pluginId, readPositionOf(pluginPath))
                }
            }
        } ?: emptyList()

    private
    fun TomlTable.readPlugin(id: String, position: ElementPosition?) =
        Plugin(id, position, getString("version"))

    private
    fun TomlParseResult.readDependencies(): Map<String, List<Dependency>> =
        getTable("dependencies")?.run {
            keySet().asSequence().mapNotNull { configName ->
                getTable(singletonList(configName))
                    ?.readConfiguration()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { deps -> configName to deps }
            }
        }?.toMap() ?: emptyMap()

    private
    fun TomlTable.readConfiguration(): List<Dependency> =
        keySet().flatMap { group ->
            getTable(singletonList(group))
                ?.readDependency(group)
                ?: emptyList()
        }

    private
    fun TomlTable.readDependency(group: String): List<Dependency> = keySet().mapNotNull { name ->
        val path = singletonList(name)
        when {
            isTable(path) -> getTable(path)?.let { table ->
                table.run {
                    when {
                        group == "project" -> Dependency.ProjectDependency(name)
                        getBoolean("platform") { false } -> Dependency.PlatformDependency(group, name, getString("version"))
                        else -> ExternalDependency(group, name, getString("version"))
                    }
                }
            }
            else -> getString(path)?.let { version ->
                ExternalDependency(group, name, version)
            }
        }
    }

    private
    fun TomlTable.readPositionOf(path: List<String>): ElementPosition? =
        inputPositionOf(path)?.let { position ->
            ElementPosition(position.line(), position.column())
        }

    private
    fun TomlParseResult.readExtensions(): List<Extension> =
        keySet().asSequence().filter {
            it !in BUILTIN_NODES
        }.mapNotNull {
            getTable(it)?.readExtension(it)
        }.toList()

    private
    fun TomlTable.readExtension(name: String): Extension =
        Extension(
            name,
            properties = keySet().associateWith { key ->
                propertyValueOf(key)
            }
        )

    private
    fun TomlTable.propertyValueOf(key: String) = when (val value = get(key)) {
        is Long -> IntValue(value.toInt())
        is Int -> IntValue(value)
        is Double -> DoubleValue(value)
        is String -> StringValue(value)
        else -> throw UnsupportedOperationException("Parsing of nested ")
    }
}
