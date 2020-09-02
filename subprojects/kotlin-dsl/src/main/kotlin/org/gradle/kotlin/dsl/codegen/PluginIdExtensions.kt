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

package org.gradle.kotlin.dsl.codegen

import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

import java.io.File
import java.io.IOException
import java.lang.IllegalArgumentException

import java.util.Properties
import java.util.jar.JarEntry
import java.util.jar.JarFile


internal
fun writeBuiltinPluginIdExtensionsTo(file: File, gradleJars: Iterable<File>) {
    file.bufferedWriter().use {
        it.apply {
            write(fileHeader)
            write("\n")
            pluginIdExtensionDeclarationsFor(gradleJars).forEach {
                write("\n")
                write(it)
                write("\n")
            }
        }
    }
}


private
fun pluginIdExtensionDeclarationsFor(jars: Iterable<File>): Sequence<String> {
    val extendedType = PluginDependenciesSpec::class.qualifiedName!!
    val extensionType = PluginDependencySpec::class.qualifiedName!!
    return pluginExtensionsFrom(jars)
        .map { (memberName, pluginId, website, implementationClass) ->
            """
            /**
             * The builtin Gradle plugin implemented by [$implementationClass].
             *
             * ${website?.let { "Visit the [plugin user guide]($it) for additional information." } ?: ""}
             *
             * @see $implementationClass
             */
            inline val $extendedType.`$memberName`: $extensionType
                get() = id("$pluginId")
            """.replaceIndent()
        }
}


private
data class PluginExtension(
    val memberName: String,
    val pluginId: String,
    val website: String?,
    val implementationClass: String
)


private
fun pluginExtensionsFrom(jars: Iterable<File>): Sequence<PluginExtension> =
    jars
        .asSequence()
        .filter { it.name.startsWith("gradle-") }
        .flatMap(::pluginExtensionsFrom)


private
fun pluginExtensionsFrom(file: File): Sequence<PluginExtension> =
    pluginEntriesFrom(file)
        .asSequence()
        .map { (id, implementationClass) ->
            val simpleId = id.substringAfter("org.gradle.")
            val website = UserGuideLink.forPlugin(simpleId)
            // One plugin extension for the simple id, e.g., "application"
            PluginExtension(simpleId, id, website, implementationClass)
        }


internal
object UserGuideLink {

    fun forPlugin(simpleId: String): String? =
        linkForPlugin[simpleId]?.let {
            "https://docs.gradle.org/current/userguide/$it"
        }

    private
    val linkForPlugin =
        mapOf(
            "antlr" to "antlr_plugin.html",
            "application" to "application_plugin.html",

            "assembler" to "native_software.html#assemblerPlugin",
            "assembler-lang" to "native_software.html#assemblerPlugin",

            "base" to "plugin_reference.html#sec:base_plugins",

            "binary-base" to "software_model_concepts.html",

            "build-dashboard" to "build_dashboard_plugin.html",

            "build-init" to "build_init_plugin.html",
            "c" to "native_software.html#sec:c_sources",
            "c-lang" to "native_software.html#sec:c_sources",

            "checkstyle" to "checkstyle_plugin.html",
            "clang-compiler" to "native_software.html",

            "codenarc" to "codenarc_plugin.html",

            // "coffeescript-base" to "coffeescript_base_plugin.html",

            "compare-gradle-builds" to "comparing_builds.html",

            "component-base" to "software_model_concepts.html",
            "component-model-base" to "software_model_concepts.html",

            "cpp" to "native_software.html#cppPlugin",
            "cpp-executable" to "native_software.html#cppPlugin",
            "cpp-lang" to "native_software.html#cppPlugin",
            "cpp-library" to "native_software.html#cppPlugin",

            "cunit" to "native_software.html#native_binaries:cunit",
            "cunit-test-suite" to "native_software.html#native_binaries:cunit",

            "distribution" to "distribution_plugin.html",
            "ear" to "ear_plugin.html",
            "eclipse" to "eclipse_plugin.html",
            "eclipse-wtp" to "eclipse_plugin.html",

            // "envjs" to "envjs_plugin.html",

            "gcc-compiler" to "native_software.html#native_binaries:tool_chain",

            "google-test" to "native_software.html#native_binaries:google_test",
            "google-test-test-suite" to "native_software.html#native_binaries:google_test",

            "groovy" to "groovy_plugin.html",
            "groovy-base" to "plugin_reference.html#sec:base_plugins",

            "help-tasks" to "command_line_interface.html#sec:command_line_project_reporting",

            "idea" to "idea_plugin.html",

            "ivy-publish" to "publishing_ivy.html#publishing_ivy:plugin",

            "jacoco" to "jacoco_plugin.html",

            "java" to "java_plugin.html",
            "java-base" to "plugin_reference.html#sec:base_plugins",

            "java-gradle-plugin" to "java_gradle_plugin.html",

            // "java-lang" to "java_software.html",

            "java-library" to "java_library_plugin.html",
            "java-library-distribution" to "java_library_distribution_plugin.html",

            // "javascript-base" to "javascript_base_plugin.html",

            // "jshint" to "jshint_plugin.html",

            // "junit-test-suite" to "java_software.html#sec:testing_java_libraries",

            // "jvm-component" to "java_software.html",
            // "jvm-resources" to "java_software.html",

            // "language-base" to "language_base_plugin.html",
            // "lifecycle-base" to "lifecycle_base_plugin.html",

            "maven" to "maven_plugin.html",
            "maven-publish" to "publishing_maven.html",

            "microsoft-visual-cpp-compiler" to "native_software.html#native_binaries:tool_chain",

            "native-component" to "native_software.html#sec:native_software_model",
            "native-component-model" to "native_software.html#sec:native_software_model",

            "objective-c" to "native_software.html#sec:objectivec_sources",
            "objective-c-lang" to "native_software.html#sec:objectivec_sources",

            "objective-cpp" to "native_software.html#sec:objectivecpp_sources",
            "objective-cpp-lang" to "native_software.html#sec:objectivecpp_sources",

            "play" to "play_plugin.html",
            "play-application" to "play_plugin.html",
            "play-cofeescript" to "play_plugin.html",
            "play-ide" to "play_plugin.html",
            "play-javascript" to "play_plugin.html",

            "pmd" to "pmd_plugin.html",

            "project-report" to "project_report_plugin.html",
            "project-reports" to "project_report_plugin.html",

            // "publishing" to "publishing_plugin.html",

            "reporting-base" to "plugin_reference.html#sec:base_plugins",

            // "rhino" to "rhino_plugin.html",

            "scala" to "scala_plugin.html",
            "scala-base" to "plugin_reference.html#sec:base_plugins",

            // "scala-lang" to "scala_lang_plugin.html",

            "signing" to "signing_plugin.html",

            "standard-tool-chains" to "native_software.html#native_binaries:tool_chain",

            "visual-studio" to "native_software.html#native_binaries:visual_studio",

            "war" to "war_plugin.html",

            "windows-resource-script" to "native_software.html#native_binaries:windows-resources",
            "windows-resources" to "native_software.html#native_binaries:windows-resources")
}


internal
data class PluginEntry(val pluginId: String, val implementationClass: String)


internal
fun pluginEntriesFrom(jar: File): List<PluginEntry> = try {
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
} catch (cause: IOException) {
    throw IllegalArgumentException(
        "Failed to extract plugin metadata from '" + jar.path + "'",
        cause
    )
}


private
fun isGradlePluginPropertiesFile(entry: JarEntry) = entry.run {
    isFile && name.run { startsWith("META-INF/gradle-plugins/") && endsWith(".properties") }
}
