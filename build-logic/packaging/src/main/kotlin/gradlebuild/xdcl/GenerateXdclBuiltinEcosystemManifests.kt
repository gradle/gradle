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
 * Writes a built-in-ecosystem manifest for every plugin carrier in the module. Each `.xdcl` that
 * declares a top-level `plugin { }` block yields a `META-INF/xdcl-builtin-ecosystem/<id>.properties`
 * naming the distribution schema module(s) the provider resolves (via `ModuleRegistry`) when that
 * plugin is applied. The id is the `.xdcl` file name — the same convention `xdcl-gradle-plugin` uses
 * to register the plugin — so a module can carry several ecosystem plugins without listing any id.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class GenerateXdclBuiltinEcosystemManifests : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val carrierFiles: ConfigurableFileCollection

    @get:Input
    abstract val schemaModules: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val dir = outputDir.get().dir("META-INF/xdcl-builtin-ecosystem").asFile
        dir.deleteRecursively()
        dir.mkdirs()
        val modules = schemaModules.get().joinToString(",")
        carrierFiles.files
            .filter { it.name.endsWith(".xdcl") && PLUGIN_BLOCK.containsMatchIn(it.readText()) }
            .forEach { carrier ->
                val pluginId = carrier.name.removeSuffix(".xdcl")
                val properties = Properties().apply { setProperty("schemaModules", modules) }
                ReproduciblePropertiesWriter.store(properties, dir.resolve("$pluginId.properties"))
            }
    }

    private companion object {
        val PLUGIN_BLOCK = Regex("""(?m)^\s*plugin\s*\{""")
    }
}
