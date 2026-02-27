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

package gradlebuild.packaging.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

private val MODULE_LINE_REGEX = Regex("""^[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+$""")
private const val LICENSE_SEPARATOR = "------------------------------------------------------------------------------"
private const val LICENSE_LIST_HEADER = "Licenses for included components:"
private const val LICENSE_FIELD_SEPARATOR = "\u001F"

private data class ManagedLicense(val title: String, val url: String? = null)
private data class ExternalModule(val group: String, val name: String, val version: String) {
    val ga: String
        get() = "$group:$name"
}

private data class LicenseSectionRange(
    val title: String,
    val modulesStart: Int,
    val modulesEnd: Int
)

@DisableCachingByDefault(because = "Simple verification task; not performance critical.")
abstract class CheckExternalDependenciesInLicenseTask : DefaultTask() {
    @get:Input
    abstract val externalModulesGa: ListProperty<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val licenseFile: RegularFileProperty

    @TaskAction
    fun check() {
        val listedModules = listedLicenseModules(licenseFile.get().asFile.readLines())
        val externalModules = externalModulesGa.get().toSet()
        val missingModules = (externalModules - listedModules).sorted()
        val unexpectedModules = (listedModules - externalModules).sorted()
        if (missingModules.isNotEmpty() || unexpectedModules.isNotEmpty()) {
            val missingReport = if (missingModules.isEmpty()) "none" else missingModules.joinToString(separator = "\n")
            val unexpectedReport = if (unexpectedModules.isEmpty()) "none" else unexpectedModules.joinToString(separator = "\n")
            error(
                """
                LICENSE dependency listing does not match used external dependencies.

                Missing from LICENSE:
                $missingReport

                Listed but not used:
                $unexpectedReport

                Run :distributions-full:updateLicenseDependencies to update LICENSE automatically.
                """.trimIndent()
            )
        }
    }
}

@DisableCachingByDefault(because = "Mutates root LICENSE file in-place.")
abstract class UpdateLicenseDependenciesTask : DefaultTask() {
    @get:Input
    abstract val externalModules: ListProperty<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val licenseFile: RegularFileProperty

    @get:Input
    abstract val pomLicenseByModule: MapProperty<String, String>

    @get:Input
    abstract val modulesMissingPomLicense: ListProperty<String>

    @TaskAction
    fun update() {
        val license = licenseFile.get().asFile
        val lines = license.readLines().toMutableList()
        require(lines.contains(LICENSE_LIST_HEADER)) { "Could not find '$LICENSE_LIST_HEADER' in LICENSE." }
        val missingPomLicenses = modulesMissingPomLicense.get().sorted()
        if (missingPomLicenses.isNotEmpty()) {
            error(
                """
                Missing <license><name> in dependency POM for:
                ${missingPomLicenses.joinToString("\n")}
                """.trimIndent()
            )
        }

        val groupedByTitle = parseExternalModules(externalModules.get())
            .map { module ->
                val encodedLicense = pomLicenseByModule.get()[module.ga]
                    ?: error("Missing resolved POM license for ${module.ga}")
                module.ga to decodeManagedLicense(encodedLicense)
            }
            .groupBy(
                keySelector = { (_, license) -> license.title },
                valueTransform = { (moduleGa, license) -> moduleGa to license.url }
            )
            .mapValues { (_, entries) ->
                val modules = entries.map { it.first }.toSortedSet()
                val url = entries.firstNotNullOfOrNull { it.second }
                ManagedLicense(title = entries.first().let { (_, _) -> "" }, url = url) to modules
            }
        val managedModules = groupedByTitle.map { (title, licenseAndModules) ->
            ManagedLicense(title = title, url = licenseAndModules.first.url) to licenseAndModules.second
        }
        val usedModules = managedModules.flatMap { it.second }.toSet()

        managedModules.forEach { (managedLicense, modules) ->
            val existingSection = findSection(lines, managedLicense.title)
            if (existingSection == null) {
                lines.addAll(lines.size, renderManagedSection(managedLicense, modules.sorted()))
            } else {
                replaceModuleRange(lines, existingSection, modules.sorted())
            }
        }

        lines.removeAll { line ->
            val module = line.trim()
            MODULE_LINE_REGEX.matches(module) && module !in usedModules
        }

        license.writeText(lines.joinToString("\n").trimEnd() + "\n")
    }
}

private fun parseExternalModules(specs: List<String>): List<ExternalModule> =
    specs.map { spec ->
        val parts = spec.split(":")
        require(parts.size == 3) { "Invalid external module spec: $spec" }
        ExternalModule(parts[0], parts[1], parts[2])
    }

private fun listedLicenseModules(lines: List<String>): Set<String> =
    lines.map(String::trim)
        .filter { MODULE_LINE_REGEX.matches(it) }
        .toSet()

private fun findSection(lines: List<String>, title: String): LicenseSectionRange? {
    for (i in 0 until lines.lastIndex) {
        if (lines[i] == LICENSE_SEPARATOR && lines[i + 1].trim() == title) {
            var cursor = i + 2
            if (cursor < lines.size && lines[cursor].startsWith("http")) cursor++
            if (cursor < lines.size && lines[cursor].isBlank()) cursor++
            val modulesStart = cursor
            var modulesEnd = modulesStart
            while (modulesEnd < lines.size && (lines[modulesEnd].isBlank() || MODULE_LINE_REGEX.matches(lines[modulesEnd].trim()))) {
                modulesEnd++
            }
            return LicenseSectionRange(title, modulesStart, modulesEnd)
        }
    }
    return null
}

private fun replaceModuleRange(lines: MutableList<String>, section: LicenseSectionRange, modules: List<String>) {
    lines.subList(section.modulesStart, section.modulesEnd).clear()
    lines.addAll(section.modulesStart, modules + listOf(""))
}

private fun renderManagedSection(license: ManagedLicense, modules: List<String>): List<String> {
    val result = mutableListOf<String>()
    result += LICENSE_SEPARATOR
    result += license.title
    license.url?.let { result += it }
    result += ""
    result += modules
    result += ""
    return result
}

private fun decodeManagedLicense(encodedValue: String): ManagedLicense {
    val parts = encodedValue.split(LICENSE_FIELD_SEPARATOR, limit = 2)
    val title = parts.firstOrNull().orEmpty()
    require(title.isNotEmpty()) { "Invalid encoded managed license value: $encodedValue" }
    val url = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
    return ManagedLicense(title, url)
}
