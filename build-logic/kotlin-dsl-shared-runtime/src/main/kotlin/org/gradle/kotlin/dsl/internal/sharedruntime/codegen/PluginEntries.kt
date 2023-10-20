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

package org.gradle.kotlin.dsl.internal.sharedruntime.codegen

import java.io.File
import java.io.IOException
import java.util.Properties
import java.util.jar.JarEntry
import java.util.jar.JarFile


data class PluginEntry(val pluginId: String, val implementationClass: String)


fun pluginEntriesFrom(jar: File): List<PluginEntry> = try {
    if (jar.isDirectory) {
        jar.walkBottomUp().asSequence().filter {
            isGradlePluginPropertiesFile(it)
        }.map { pluginEntry ->
            val pluginProperties = pluginEntry.inputStream().use { Properties().apply { load(it) } }
            val id = pluginEntry.name.substringBeforeLast(".properties")
            val implementationClass = pluginProperties.getProperty("implementation-class")
            PluginEntry(id, implementationClass)
        }.toList()
    } else {
        JarFile(jar, false).use { jarFile ->
            jarFile.entries().asSequence().filter {
                isGradlePluginPropertiesFile(it)
            }.map { pluginEntry ->
                val pluginProperties = jarFile.getInputStream(pluginEntry).use { Properties().apply { load(it) } }
                val id = pluginEntry.name.substringAfterLast("/").substringBeforeLast(".properties")
                val implementationClass = pluginProperties.getProperty("implementation-class")
                PluginEntry(id, implementationClass)
            }.toList()
        }
    }
} catch (cause: IOException) {
    throw IllegalArgumentException(
        "Failed to extract plugin metadata from '" + jar.path + "'",
        cause
    )
}


private
fun isGradlePluginPropertiesFile(entry: JarEntry) = entry.run {
    !isDirectory && name.run { startsWith("META-INF/gradle-plugins/") && endsWith(".properties") }
}


private
fun isGradlePluginPropertiesFile(entry: File): Boolean = entry.run {
    !isDirectory && name.endsWith(".properties") && parentFile?.name == "gradle-plugins" && parentFile?.parentFile?.name == "META-INF"
}
