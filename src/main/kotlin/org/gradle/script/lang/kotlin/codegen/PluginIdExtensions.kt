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

package org.gradle.script.lang.kotlin.codegen

import org.gradle.script.lang.kotlin.KotlinPluginDependenciesHandler

import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

import java.io.File
import java.util.*

import java.util.jar.JarFile

internal
fun writeBuiltinPluginIdExtensionsTo(file: File, gradleJars: Iterable<File>) {
    file.bufferedWriter().use { it.apply {
        write(fileHeader)
        write("\n")
        write("import ${PluginDependenciesSpec::class.qualifiedName}\n")
        write("import ${PluginDependencySpec::class.qualifiedName}\n")
        pluginIdExtensionDeclarationsFor(gradleJars).forEach {
            write("\n")
            write(it)
            write("\n")
        }
    }}
}

private
fun pluginIdExtensionDeclarationsFor(jars: Iterable<File>): Sequence<String> {
    val extendedType = PluginDependenciesSpec::class.simpleName
    val extensionType = PluginDependencySpec::class.simpleName
    return jars
        .asSequence()
        .filter { it.name.startsWith("gradle-") }
        .flatMap(::pluginExtensionsFrom)
        .map { (memberName, pluginId, website, implementationClass) ->
            """
            /**
             * The builtin Gradle plugin implemented by [$implementationClass].
             *
             * Visit the [plugin user guide]($website) for additional information.
             *
             * @see $implementationClass
             */
            inline val $extendedType.`$memberName`: $extensionType
                get() = id("$pluginId")
            """.replaceIndent()
        }
}

private
data class PluginExtension(val memberName: String, val pluginId: String, val website: String, val implementationClass: String)

private
fun pluginExtensionsFrom(file: File): Sequence<PluginExtension> =
    pluginEntriesFrom(file)
        .asSequence()
        .flatMap { (id, implementationClass) ->
            val simpleId = id.substringAfter("org.gradle.")
            val website = "https://docs.gradle.org/current/userguide/${simpleId}_plugin.html"
            sequenceOf(
                // One plugin extension for the simple id, e.g., "application"
                PluginExtension(simpleId, id, website, implementationClass),
                // And another extension for the full id, e.g., "org.gradle.application"
                // but since the regular full-stop dot is not a valid member name character in Kotlin,
                // the ONE DOT LEADER character ('\u2024') is being used as a replacement here.
                PluginExtension(id.replace('.', '․'), id, website, implementationClass))
        }

private
data class PluginEntry(val pluginId: String, val implementationClass: String)

private
fun pluginEntriesFrom(jar: File): List<PluginEntry> =
    JarFile(jar).use { jarFile ->
        jarFile.entries().asSequence().filter {
            it.isFile && it.name.startsWith("META-INF/gradle-plugins/")
        }.map { pluginEntry ->
            val pluginProperties = jarFile.getInputStream(pluginEntry).use { Properties().apply { load(it) } }
            val id = pluginEntry.name.substringAfterLast("/").substringBeforeLast(".properties")
            val implementationClass = pluginProperties.getProperty("implementation-class")
            PluginEntry(id, implementationClass)
        }.toList()
    }

