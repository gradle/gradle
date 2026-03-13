/*
 * Copyright 2025 the original author or authors.
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

import gradlebuild.packaging.support.PomLicenseUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.Properties


private const val EQUALS_SEPARATOR = "=============================================================================="
private const val DASH_SEPARATOR = "------------------------------------------------------------------------------"


/**
 * Generates the component license section of the Gradle distribution LICENSE file.
 *
 * The task combines the base Apache 2.0 license text with an auto-generated section listing
 * every third-party component bundled in the distribution along with its license information,
 * derived from the components' Maven POM files.
 *
 * POM files (direct and parent) are pre-resolved at configuration time by the build convention
 * plugin and supplied via [pomFiles]. The task only reads files at execution time, making it
 * fully compatible with the configuration cache.
 *
 * If the direct POM has no [<licenses>] section, the parent POM chain is walked using the
 * pre-resolved [pomFiles] until a license declaration is found.
 *
 * The task fails with a descriptive error if any external component has no license data —
 * this ensures that new dependencies are always explicitly accounted for.
 *
 * For components whose POM has missing or incorrect license information, add a hardcoded
 * entry to [hardcodedLicenses] below.
 *
 * License name normalization is applied to group components that declare the same license
 * under different names. Add normalization entries in [licenseNameNormalization] when
 * a new POM uses an unexpected spelling for a known license.
 */
abstract class GenerateLicenseFile : DefaultTask() {

    /**
     * The Apache 2.0 license header text (the root LICENSE file, without the component section).
     * The generated component section is appended after this content.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val baseLicenseFile: RegularFileProperty

    /**
     * Module .properties files produced by [GenerateClasspathModuleProperties], one per component
     * in the resolved dependency graph. Files for external components contain alias.group,
     * alias.name, alias.version; project component files do not (and are therefore skipped).
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val modulePropertiesFiles: ConfigurableFileCollection

    /**
     * All Maven POM files needed to resolve license information, including parent POMs for
     * components that inherit their license declaration. Populated at configuration time by
     * the build convention plugin via a lazy provider, so the task itself requires no project
     * access and is fully configuration-cache compatible.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val pomFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputLicenseFile: RegularFileProperty

    @TaskAction
    fun generate() {
        // Collect all external components from module properties files
        // (properties files for project components do not have alias.group, so they are skipped)
        val externalComponents = mutableSetOf<ExternalComponent>()
        for (propsFile in modulePropertiesFiles.files) {
            if (!propsFile.exists()) continue
            val props = Properties().also { it.load(propsFile.reader()) }
            val group = props.getProperty("alias.group") ?: continue
            val name = props.getProperty("alias.name") ?: continue
            val version = props.getProperty("alias.version") ?: continue
            externalComponents.add(ExternalComponent(group, name, version))
        }

        // Build a map of POM info from all pre-resolved POM files
        val pomMap = buildPomMap()

        // Resolve license for each external component by walking the pre-resolved POM chain
        val licenseByCoords = mutableMapOf<String, ComponentLicenseInfo>()
        for (component in externalComponents) {
            val info = lookupLicense(component, pomMap)
            if (info != null) {
                licenseByCoords[component.coordKey] = info
            }
        }

        // Fail if any external component has no license data
        val missing = externalComponents
            .filter { it.coordKey !in licenseByCoords }
            .map { "${it.coordKey}:${it.version}" }
            .sorted()
        if (missing.isNotEmpty()) {
            throw GradleException(
                "The following external dependencies have no license information in their POM.\n" +
                "Add a hardcoded entry to GenerateLicenseFile.hardcodedLicenses for each:\n" +
                missing.joinToString("\n") { "  - $it" }
            )
        }

        // Group components by license display name (sorted alphabetically)
        val byLicense = externalComponents
            .groupBy { licenseByCoords.getValue(it.coordKey).displayName }
            .toSortedMap()

        // Format the output
        val output = buildString {
            append(baseLicenseFile.get().asFile.readText().trimEnd())
            append("\n\n\n")
            append(EQUALS_SEPARATOR)
            append("\n")
            append("Licenses for included components:")
            append("\n")

            for ((licenseName, components) in byLicense) {
                val url = licenseByCoords[components.first().coordKey]?.url
                append("\n")
                append(DASH_SEPARATOR)
                append("\n")
                append(licenseName)
                append("\n")
                if (url != null) {
                    append(url)
                    append("\n")
                }
                append("\n")
                for (component in components.sortedBy { it.coordKey }) {
                    append("${component.group}:${component.name}")
                    append("\n")
                }
            }
        }

        outputLicenseFile.get().asFile.writeText(output)
    }

    /**
     * Builds a map of POM info from all files in [pomFiles], keyed by "group:artifact:version"
     * (and "group:artifact" as a fallback for version-less lookups).
     */
    private fun buildPomMap(): Map<String, PomLicenseUtils.PomInfo> {
        val map = mutableMapOf<String, PomLicenseUtils.PomInfo>()
        for (pomFile in pomFiles.files) {
            val info = PomLicenseUtils.parsePom(pomFile)
            val g = info.groupId ?: continue
            val a = info.artifactId ?: continue
            val v = info.version
            if (v != null) map["$g:$a:$v"] = info
            map.putIfAbsent("$g:$a", info)
        }
        return map
    }

    /**
     * Looks up the license for a component by walking up the parent POM chain in [pomMap].
     * Checks [hardcodedLicenses] first, then follows [<parent>] links in the POM map.
     * No project access — only reads from the pre-built map of POM files.
     */
    private fun lookupLicense(
        component: ExternalComponent,
        pomMap: Map<String, PomLicenseUtils.PomInfo>,
    ): ComponentLicenseInfo? {
        val override = hardcodedLicenses[component.coordKey]
        if (override != null) {
            val display = licenseNameNormalization[override.first] ?: override.first
            return ComponentLicenseInfo(display, override.second)
        }

        var lookupKey = "${component.group}:${component.name}:${component.version}"
        for (depth in 0..10) {
            val pomInfo = pomMap[lookupKey]
                ?: pomMap[lookupKey.substringBeforeLast(":")]  // fallback: without version
                ?: break
            if (pomInfo.licenseName != null) {
                val display = licenseNameNormalization[pomInfo.licenseName] ?: pomInfo.licenseName
                return ComponentLicenseInfo(display, pomInfo.licenseUrl)
            }
            val pg = pomInfo.parentGroupId ?: break
            val pa = pomInfo.parentArtifactId ?: break
            val pv = pomInfo.parentVersion ?: break
            lookupKey = "$pg:$pa:$pv"
        }
        return null
    }

    private data class ExternalComponent(val group: String, val name: String, val version: String) {
        val coordKey: String get() = "$group:$name"
    }

    private data class ComponentLicenseInfo(val displayName: String, val url: String?)
}


/**
 * Hardcoded license data for components with missing or incorrect POM license information.
 * Key format: "groupId:artifactId"
 * Value: Pair(licenseName, licenseUrl?) — licenseUrl may be null if not applicable.
 *
 * Add entries here when the build fails with "no license information found" for a component.
 */
private val hardcodedLicenses: Map<String, Pair<String, String?>> = mapOf(
    // net.rubygrapefruit:native-platform and all its platform-specific variants do not include
    // <licenses> in their POM but are Apache 2.0 licensed (https://github.com/gradle/native-platform)
    "net.rubygrapefruit:native-platform" to ("Apache License, Version 2.0" to "https://www.apache.org/licenses/LICENSE-2.0"),
    "net.rubygrapefruit:native-platform-freebsd-amd64-libcpp" to ("Apache License, Version 2.0" to "https://www.apache.org/licenses/LICENSE-2.0"),
    "net.rubygrapefruit:native-platform-linux-aarch64" to ("Apache License, Version 2.0" to "https://www.apache.org/licenses/LICENSE-2.0"),
    "net.rubygrapefruit:native-platform-linux-aarch64-ncurses5" to ("Apache License, Version 2.0" to "https://www.apache.org/licenses/LICENSE-2.0"),
    "net.rubygrapefruit:native-platform-linux-aarch64-ncurses6" to ("Apache License, Version 2.0" to "https://www.apache.org/licenses/LICENSE-2.0"),
    "net.rubygrapefruit:native-platform-linux-amd64" to ("Apache License, Version 2.0" to "https://www.apache.org/licenses/LICENSE-2.0"),
    "net.rubygrapefruit:native-platform-linux-amd64-ncurses5" to ("Apache License, Version 2.0" to "https://www.apache.org/licenses/LICENSE-2.0"),
    "net.rubygrapefruit:native-platform-linux-amd64-ncurses6" to ("Apache License, Version 2.0" to "https://www.apache.org/licenses/LICENSE-2.0"),
    "net.rubygrapefruit:native-platform-osx-aarch64" to ("Apache License, Version 2.0" to "https://www.apache.org/licenses/LICENSE-2.0"),
    "net.rubygrapefruit:native-platform-osx-amd64" to ("Apache License, Version 2.0" to "https://www.apache.org/licenses/LICENSE-2.0"),
    "net.rubygrapefruit:native-platform-windows-aarch64" to ("Apache License, Version 2.0" to "https://www.apache.org/licenses/LICENSE-2.0"),
    "net.rubygrapefruit:native-platform-windows-aarch64-min" to ("Apache License, Version 2.0" to "https://www.apache.org/licenses/LICENSE-2.0"),
    "net.rubygrapefruit:native-platform-windows-amd64" to ("Apache License, Version 2.0" to "https://www.apache.org/licenses/LICENSE-2.0"),
    "net.rubygrapefruit:native-platform-windows-amd64-min" to ("Apache License, Version 2.0" to "https://www.apache.org/licenses/LICENSE-2.0"),
    "net.rubygrapefruit:native-platform-windows-i386" to ("Apache License, Version 2.0" to "https://www.apache.org/licenses/LICENSE-2.0"),
    "net.rubygrapefruit:native-platform-windows-i386-min" to ("Apache License, Version 2.0" to "https://www.apache.org/licenses/LICENSE-2.0"),
)


/**
 * Maps raw POM license names to canonical display names for consistent section grouping.
 *
 * POM files across the ecosystem use inconsistent strings for the same licenses
 * (e.g. "The Apache Software License, Version 2.0" vs "Apache-2.0").
 * This map normalizes them to the canonical name used in the LICENSE output.
 *
 * Add entries here when the build encounters an unexpected name for a license that is
 * already represented in the output (and that should be grouped with existing entries).
 */
private val licenseNameNormalization: Map<String, String> = mapOf(
    // Apache 2.0 variants
    "The Apache Software License, Version 2.0" to "Apache License, Version 2.0",
    "Apache Software License - Version 2.0" to "Apache License, Version 2.0",
    "Apache License 2.0" to "Apache License, Version 2.0",
    "Apache-2.0" to "Apache License, Version 2.0",
    "Apache 2.0" to "Apache License, Version 2.0",
    "Apache 2" to "Apache License, Version 2.0",
    "ASL, version 2" to "Apache License, Version 2.0",

    // MIT variants
    "The MIT License" to "MIT License",
    "MIT" to "MIT License",

    // BSD variants
    "BSD 3-Clause License" to "3-Clause BSD License",
    "BSD-3-Clause" to "3-Clause BSD License",
    "New BSD License" to "3-Clause BSD License",
    "The New BSD License" to "3-Clause BSD License",
    "BSD" to "3-Clause BSD License",

    // Eclipse Public License variants
    "EPL-1.0" to "Eclipse Public License 1.0",
    "EPL-2.0" to "Eclipse Public License 2.0",

    // LGPL variants
    "GNU Lesser General Public License" to "LGPL 2.1",
    "GNU Lesser General Public License, Version 2.1" to "LGPL 2.1",
    "LGPL-2.1-or-later" to "LGPL 2.1",
)
