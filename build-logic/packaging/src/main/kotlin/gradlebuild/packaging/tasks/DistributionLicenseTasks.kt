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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

private val MODULE_LINE_REGEX = Regex("""^[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+$""")
private const val LICENSE_SEPARATOR = "------------------------------------------------------------------------------"
private const val LICENSE_LIST_HEADER = "Licenses for included components:"
private const val GENERATED_LICENSE_START_MARKER = "=== BEGIN GENERATED DISTRIBUTION DEPENDENCY LICENSES ==="
private const val GENERATED_LICENSE_END_MARKER = "=== END GENERATED DISTRIBUTION DEPENDENCY LICENSES ==="

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

private val MANAGED_LICENSES = listOf(
    ManagedLicense("Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    ManagedLicense("Eclipse Public License 1.0", "https://opensource.org/licenses/EPL-1.0"),
    ManagedLicense("3-Clause BSD", "https://opensource.org/licenses/BSD-3-Clause"),
    ManagedLicense("MIT", "https://opensource.org/licenses/MIT"),
    ManagedLicense("CDDL", "https://opensource.org/licenses/CDDL-1.0"),
    ManagedLicense("LGPL 2.1", "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.en.html"),
    ManagedLicense("Eclipse Distribution License 1.0", "https://www.eclipse.org/org/documents/edl-v10.php"),
    ManagedLicense("BSD-style"),
    ManagedLicense("Eclipse Public License 2.0", "https://www.eclipse.org/legal/epl-2.0/"),
    ManagedLicense("Mozilla Public License 2.0", "https://www.mozilla.org/en-US/MPL/2.0/")
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
        val cleanedLicense = removeLegacyGeneratedBlock(licenseFile.get().asFile.readText())
        val listedModules = listedLicenseModules(cleanedLicense.lines())
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

    @TaskAction
    fun update() {
        val license = licenseFile.get().asFile
        val cleanedContent = removeLegacyGeneratedBlock(license.readText())
        val lines = cleanedContent.lines().toMutableList()
        val headerIndex = lines.indexOf(LICENSE_LIST_HEADER)
        require(headerIndex >= 0) { "Could not find '$LICENSE_LIST_HEADER' in LICENSE." }
        val legacyAssignments = legacyLicenseAssignments(lines)

        val modulesByLicense = parseExternalModules(externalModules.get()).groupBy { module ->
            legacyAssignments[module.ga] ?: fallbackLicenseForModule(module)
        }
        val unknownModules = modulesByLicense[null].orEmpty().map { it.ga }.sorted()
        if (unknownModules.isNotEmpty()) {
            error(
                """
                Could not determine a managed LICENSE section for:
                ${unknownModules.joinToString("\n")}

                Please add mapping support in UpdateLicenseDependenciesTask for these modules.
                """.trimIndent()
            )
        }

        val managedModules = modulesByLicense.filterKeys { it != null }
            .mapKeys { it.key!! }
            .mapValues { (_, modules) -> modules.map { it.ga }.toSet() }
        val usedModules = managedModules.values.flatten().toSet()

        val sections = MANAGED_LICENSES.mapNotNull { findSection(lines, it.title) }
        sections.sortedByDescending { it.modulesStart }.forEach { section ->
            val licenseForSection = managedLicenseByTitle(section.title)
            replaceModuleRange(lines, section, managedModules[licenseForSection].orEmpty().sorted())
        }

        val existingTitles = sections.map { it.title }.toSet()
        val missingSections = MANAGED_LICENSES.filter { it.title !in existingTitles && !managedModules[it].isNullOrEmpty() }
        if (missingSections.isNotEmpty()) {
            val rendered = missingSections.flatMap { renderManagedSection(it, managedModules[it].orEmpty().sorted()) }
            lines.addAll(lines.size, rendered)
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

private fun managedLicenseByTitle(title: String): ManagedLicense =
    MANAGED_LICENSES.first { it.title == title }

private fun fallbackLicenseForModule(module: ExternalModule): ManagedLicense? {
    val group = module.group
    return when {
        group.startsWith("com.amazonaws") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("org.apache.") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("org.gradle.") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("com.fasterxml.jackson") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("com.google.") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("com.github.jnr") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("com.github.javaparser") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("com.esotericsoftware.kryo") -> managedLicenseByTitle("3-Clause BSD")
        group.startsWith("com.esotericsoftware.minlog") -> managedLicenseByTitle("3-Clause BSD")
        group.startsWith("com.squareup") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("commons-") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("io.grpc") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("io.opencensus") -> managedLicenseByTitle("Apache 2.0")
        group == "it.unimi.dsi" -> managedLicenseByTitle("Apache 2.0")
        group == "javax.inject" -> managedLicenseByTitle("Apache 2.0")
        group == "joda-time" -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("net.rubygrapefruit") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("org.codehaus.plexus") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("org.sonatype.plexus") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("org.jetbrains.kotlin") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("org.jetbrains.kotlinx") -> managedLicenseByTitle("Apache 2.0")
        group == "org.jetbrains" -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("org.jctools") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("org.jspecify") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("org.objenesis") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("org.slf4j") -> managedLicenseByTitle("MIT")
        group.startsWith("org.tomlj") -> managedLicenseByTitle("Apache 2.0")
        group.startsWith("org.yaml") -> managedLicenseByTitle("Apache 2.0")
        group == "jquery" -> managedLicenseByTitle("MIT")
        group.startsWith("org.bouncycastle") -> managedLicenseByTitle("MIT")
        group.startsWith("net.java.dev.jna") -> managedLicenseByTitle("LGPL 2.1")
        group.startsWith("com.github.mwiede") -> managedLicenseByTitle("BSD-style")
        group.startsWith("org.eclipse.jgit") -> managedLicenseByTitle("Eclipse Distribution License 1.0")
        group == "jcifs" || group == "org.samba.jcifs" -> managedLicenseByTitle("LGPL 2.1")
        else -> null
    }
}

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

private fun legacyLicenseAssignments(lines: List<String>): Map<String, ManagedLicense> =
    MANAGED_LICENSES.mapNotNull { license ->
        findSection(lines, license.title)?.let { section ->
            val modules = lines.subList(section.modulesStart, section.modulesEnd)
                .map(String::trim)
                .filter { MODULE_LINE_REGEX.matches(it) }
            license to modules
        }
    }.flatMap { (license, modules) ->
        modules.map { module -> module to license }
    }.toMap()

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

private fun removeLegacyGeneratedBlock(content: String): String {
    val start = content.indexOf(GENERATED_LICENSE_START_MARKER)
    val end = content.indexOf(GENERATED_LICENSE_END_MARKER)
    return if (start >= 0 && end > start) {
        val endExclusive = end + GENERATED_LICENSE_END_MARKER.length
        (content.substring(0, start) + content.substring(endExclusive)).replace(Regex("\n{3,}"), "\n\n").trimEnd() + "\n"
    } else {
        content
    }
}
