/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.xdcl

import gradlebuild.basics.util.ReproduciblePropertiesWriter
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.util.Properties

/**
 * Writes the manifest resources for every plugin carrier in the module. A plugin carrier is an
 * `.xdcl` under `src/main/xdcl/` that declares a top-level `plugin { }` block; its id is the file
 * name (the same convention `xdcl-gradle-plugin` uses to register the plugin), so a module can
 * carry several ecosystem plugins without listing any id. For each carrier this emits:
 *
 * - `META-INF/gradle-plugins/<id>.properties` — the standard Gradle plugin descriptor binding the
 *   id to its implementation class. Distribution ecosystem modules don't apply `java-gradle-plugin`
 *   (that is the external plugin-development plugin, unused for bundled plugins), so the descriptor
 *   `java-gradle-plugin` would otherwise synthesize from the `gradlePlugin {}` extension is written
 *   here instead, matching how every other Gradle bundled plugin ships a hand-authored descriptor.
 * - `META-INF/xdcl-builtin-ecosystem/<id>.properties` — names the distribution schema module(s) the
 *   provider resolves (via `ModuleRegistry`) when that plugin is applied.
 *
 * The implementation class mirrors `xdcl-gradle-plugin`'s own resolution
 * (`xdcl.gradleplugin.internal.PluginRegistrationReader` / `CarrierNaming`): the `implementationClass`
 * symbol declared in the `plugin { }` block if present, else the deterministically-named generated
 * carrier. The two must stay identical so the `java-gradle-plugin` path (external authors) and this
 * distribution path can never disagree on a carrier's entry point.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class GenerateXdclCarrierManifests : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val carrierFiles: ConfigurableFileCollection

    @get:Input
    abstract val schemaModules: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val root = outputDir.get().asFile
        val descriptorsDir = root.resolve("META-INF/gradle-plugins")
        val markersDir = root.resolve("META-INF/xdcl-builtin-ecosystem")
        descriptorsDir.deleteRecursively()
        markersDir.deleteRecursively()
        descriptorsDir.mkdirs()
        markersDir.mkdirs()

        val modules = schemaModules.get().joinToString(",")
        carrierFiles.files
            .filter { it.name.endsWith(".xdcl") }
            .map { it to it.readText() }
            .filter { (_, text) -> PLUGIN_BLOCK.containsMatchIn(text) }
            .forEach { (carrier, text) ->
                val pluginId = carrier.name.removeSuffix(".xdcl")

                val implementationClass = IMPLEMENTATION_CLASS.find(text)?.groupValues?.get(1) ?: carrierFqn(pluginId)
                val descriptor = Properties().apply { setProperty("implementation-class", implementationClass) }
                ReproduciblePropertiesWriter.store(descriptor, descriptorsDir.resolve("$pluginId.properties"))

                val marker = Properties().apply { setProperty("schemaModules", modules) }
                ReproduciblePropertiesWriter.store(marker, markersDir.resolve("$pluginId.properties"))
            }
    }

    private companion object {
        val PLUGIN_BLOCK = Regex("""(?m)^\s*plugin\s*\{""")

        /** A `plugin { implementationClass :some.Fqn }` override; the value is an XDCL symbol (`:Fqn`). */
        val IMPLEMENTATION_CLASS = Regex("""(?m)^\s*implementationClass\s+:\s*([\w.]+)""")

        /**
         * The generated carrier FQN for a plugin id, mirroring `xdcl.gradleplugin.internal.CarrierNaming`:
         * the id PascalCased over `.`/`-`/`_` separators + `Plugin`, in package `xdcl.generated.plugins`.
         */
        fun carrierFqn(pluginId: String): String {
            val simpleName = pluginId.split('.', '-', '_')
                .filter { it.isNotEmpty() }
                .joinToString("") { segment -> segment.replaceFirstChar { it.uppercaseChar() } } + "Plugin"
            return "xdcl.generated.plugins.$simpleName"
        }
    }
}
