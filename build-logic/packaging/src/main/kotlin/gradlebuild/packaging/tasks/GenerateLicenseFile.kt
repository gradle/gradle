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

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory


private const val EQUALS_SEPARATOR = "=============================================================================="
private const val DASH_SEPARATOR = "------------------------------------------------------------------------------"


/**
 * Generates the component license section of the Gradle distribution LICENSE file.
 *
 * The task combines the base Apache 2.0 license text with an auto-generated section listing
 * every third-party component bundled in the distribution along with its license information,
 * derived from the components' Maven POM files.
 *
 * The task fails with a descriptive error if any external component has no license data —
 * this ensures that new dependencies are always explicitly accounted for.
 *
 * For components whose direct POM has no [<licenses>] section, the task resolves the parent
 * POM chain via Gradle's dependency resolution (using a detached configuration) and walks
 * up until a license declaration is found.
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
     * JSON files produced by [gradlebuild.packaging.transforms.ExtractPomLicenseData], one per
     * external dependency. Each file contains: groupId, artifactId, version, licenseName, licenseUrl,
     * and optionally parentGroupId, parentArtifactId, parentVersion.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val pomLicenseFiles: ConfigurableFileCollection

    /**
     * Module .properties files produced by [GenerateClasspathModuleProperties], one per component
     * in the resolved dependency graph. Files for external components contain alias.group,
     * alias.name, alias.version; project component files do not (and are therefore skipped).
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val modulePropertiesFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputLicenseFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val gson = Gson()
        val mapType = object : TypeToken<Map<String, String?>>() {}.type

        // Build the map: "group:name" -> ComponentLicenseInfo from POM JSON files.
        // Also collect parent coordinates for components whose direct POM has no license.
        val licenseByCoords = mutableMapOf<String, ComponentLicenseInfo>()
        val missingWithParent = mutableMapOf<String, ParentCoords>()
        for (jsonFile in pomLicenseFiles.files) {
            if (!jsonFile.exists() || jsonFile.length() == 0L) continue
            val parsed: Map<String, String?> = gson.fromJson(jsonFile.readText(), mapType)
            val groupId = parsed["groupId"] ?: continue
            val artifactId = parsed["artifactId"] ?: continue
            val rawLicenseName = parsed["licenseName"]
            val licenseUrl = parsed["licenseUrl"]
            if (rawLicenseName != null) {
                val displayName = licenseNameNormalization[rawLicenseName] ?: rawLicenseName
                licenseByCoords["$groupId:$artifactId"] = ComponentLicenseInfo(displayName, licenseUrl)
            } else {
                // No license in direct POM — record parent coords for later resolution
                val pg = parsed["parentGroupId"]
                val pa = parsed["parentArtifactId"]
                val pv = parsed["parentVersion"]
                if (pg != null && pa != null && pv != null) {
                    missingWithParent["$groupId:$artifactId"] = ParentCoords(pg, pa, pv)
                }
            }
        }

        // Resolve parent POM chain for components without a direct license declaration
        for ((coordKey, parentCoords) in missingWithParent) {
            val resolved = resolveParentLicense(parentCoords.group, parentCoords.artifact, parentCoords.version)
            if (resolved != null) {
                licenseByCoords[coordKey] = resolved
            }
        }

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

        // Fail if any external component has no license data
        val missing = externalComponents
            .filter { it.coordKey !in licenseByCoords }
            .map { "${it.coordKey}:${it.version}" }
            .sorted()
        if (missing.isNotEmpty()) {
            throw GradleException(
                "The following external dependencies have no license information in their POM.\n" +
                "Add a hardcoded entry to ExtractPomLicenseData.hardcodedLicenses for each:\n" +
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
     * Resolves the license for a component by walking up its parent POM chain.
     * Uses a Gradle detached configuration to resolve each parent POM, which will hit
     * the module cache since Gradle already downloaded these during dependency resolution.
     */
    private fun resolveParentLicense(group: String, artifact: String, version: String, depth: Int = 0): ComponentLicenseInfo? {
        if (depth > 10) return null
        val pomFile = project.configurations
            .detachedConfiguration(project.dependencies.create("$group:$artifact:$version@pom"))
            .apply { isTransitive = false }
            .singleFile
        val parsed = parsePomFile(pomFile)
        val rawName = parsed["licenseName"]
        if (rawName != null) {
            val display = licenseNameNormalization[rawName] ?: rawName
            return ComponentLicenseInfo(display, parsed["licenseUrl"])
        }
        val pg = parsed["parentGroupId"] ?: return null
        val pa = parsed["parentArtifactId"] ?: return null
        val pv = parsed["parentVersion"] ?: return null
        return resolveParentLicense(pg, pa, pv, depth + 1)
    }

    private fun parsePomFile(pomFile: File): Map<String, String?> {
        val doc = try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFile)
        } catch (e: Exception) {
            return emptyMap()
        }
        doc.documentElement.normalize()
        val root = doc.documentElement

        fun Node.directChildText(name: String): String? =
            childNodes.items().firstOrNull { it.nodeName == name }
                ?.textContent?.trim()?.takeIf { it.isNotBlank() }

        val licensesNode = root.childNodes.items().firstOrNull { it.nodeName == "licenses" }
        val firstLicense = licensesNode?.childNodes?.items()?.firstOrNull { it.nodeName == "license" }
        val licenseName = firstLicense?.childNodes?.items()
            ?.firstOrNull { it.nodeName == "name" }
            ?.textContent?.trim()?.takeIf { it.isNotBlank() }
        val licenseUrl = firstLicense?.childNodes?.items()
            ?.firstOrNull { it.nodeName == "url" }
            ?.textContent?.trim()?.takeIf { it.isNotBlank() }

        val parentElement = root.childNodes.items().firstOrNull { it.nodeName == "parent" } as? Element
        return mapOf(
            "licenseName" to licenseName,
            "licenseUrl" to licenseUrl,
            "parentGroupId" to parentElement?.directChildText("groupId"),
            "parentArtifactId" to parentElement?.directChildText("artifactId"),
            "parentVersion" to parentElement?.directChildText("version"),
        )
    }

    private fun NodeList.items(): Sequence<Node> = sequence {
        for (i in 0 until length) yield(item(i))
    }

    private data class ExternalComponent(val group: String, val name: String, val version: String) {
        val coordKey: String get() = "$group:$name"
    }

    private data class ComponentLicenseInfo(val displayName: String, val url: String?)

    private data class ParentCoords(val group: String, val artifact: String, val version: String)
}


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
