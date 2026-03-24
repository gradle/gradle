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

import gradlebuild.modules.model.License
import gradlebuild.packaging.support.PomLicenseUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.Properties


/** Maximum depth when walking the parent POM chain to find a license declaration. */
private const val MAX_PARENT_POM_DEPTH = 10

private const val SEPARATOR_LENGTH = 80
private val MAIN_LICENSE_SEPARATOR = "=".repeat(SEPARATOR_LENGTH)
private val COMPONENT_LICENSE_SEPARATOR = "-".repeat(SEPARATOR_LENGTH)


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
 * If the direct POM has no `<licenses>` section, the parent POM chain is walked using the
 * pre-resolved [pomFiles] until a license declaration is found.
 *
 * The task fails with descriptive errors if:
 * - a component's POM chain contains a license name not registered in [License] — add it
 *   as an alias or new entry in `License.kt`
 * - a component has no license data in its POM or any parent POM — add an entry
 *   to [licenseOverrides] below (supports both "groupId:artifactId" and "groupId" keys)
 */
@CacheableTask
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
        // Collect all external components from module properties files.
        // Properties files for project components do not have alias.group so they map to null.
        // Deduplicate by coordKey (group:name) to avoid duplicate output lines in case the same
        // artifact appears with multiple versions in the dependency graph.
        val externalComponents = modulePropertiesFiles.files
            .filter { it.exists() }
            .mapNotNull { propsFile ->
                val props = Properties().also { p -> p.load(propsFile.reader()) }
                val group = props.getProperty("alias.group") ?: return@mapNotNull null
                val name = props.getProperty("alias.name") ?: return@mapNotNull null
                val version = props.getProperty("alias.version") ?: return@mapNotNull null
                ExternalComponent(group, name, version)
            }
            .distinctBy { it.coordKey }

        // Build a map of POM info from all pre-resolved POM files
        val pomMap = buildPomMap()

        // Resolve license for each external component by walking the pre-resolved POM chain
        val licenseByCoords = mutableMapOf<String, ComponentLicenseInfo>()
        val notFound = mutableListOf<String>()    // no license data in POM chain at all
        val unknownName = mutableListOf<String>() // license name found but not registered in License enum

        for (component in externalComponents) {
            when (val result = lookupLicense(component, pomMap)) {
                is LicenseLookupResult.Found ->
                    licenseByCoords[component.coordKey] = result.info
                is LicenseLookupResult.UnknownName ->
                    unknownName += "  ${component.coordKey}:${component.version} — '${result.rawName}'"
                LicenseLookupResult.NotFound ->
                    notFound += "  ${component.coordKey}:${component.version}"
            }
        }

        // Fail with actionable messages if any licenses could not be resolved
        val errors = buildList {
            if (unknownName.isNotEmpty()) add(
                "The following dependencies declare a license name not registered in License.kt.\n" +
                "Add it as an alias of an existing License entry, or add a new License entry in:\n" +
                "  build-logic/dependency-modules/src/main/kotlin/gradlebuild/modules/model/License.kt\n" +
                unknownName.sorted().joinToString("\n")
            )
            if (notFound.isNotEmpty()) add(
                "The following dependencies have no license data in their POM or any parent POM.\n" +
                "Add an entry to the licenseOverrides map in GenerateLicenseFile.kt.\n" +
                "Use \"groupId:artifactId\" for a single artifact or \"groupId\" for an entire group:\n" +
                notFound.sorted().joinToString("\n")
            )
        }
        if (errors.isNotEmpty()) throw GradleException(errors.joinToString("\n\n"))

        // Group components by license display name; sections and components both sorted
        // alphabetically to guarantee a deterministic, reproducible output.
        val byLicense = externalComponents
            .sortedBy { it.coordKey }
            .groupBy { licenseByCoords.getValue(it.coordKey).displayName }
            .toSortedMap()

        // Format the output
        val output = buildString {
            append(baseLicenseFile.get().asFile.readText().trimEnd())
            append("\n\n\n")
            append(MAIN_LICENSE_SEPARATOR)
            append("\n")
            append("Licenses for included components:")
            append("\n")

            for ((licenseName, components) in byLicense) {
                val url = licenseByCoords[components.first().coordKey]?.url
                append("\n")
                append(COMPONENT_LICENSE_SEPARATOR)
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
    private fun buildPomMap(): Map<String, PomLicenseUtils.PomInfo> =
        pomFiles.files
            .mapNotNull(PomLicenseUtils::parsePom)
            .flatMap { info ->
                buildList {
                    info.version?.let { v -> add("${info.groupId}:${info.artifactId}:$v" to info) }
                    add("${info.groupId}:${info.artifactId}" to info)
                }
            }
            .toMap()

    /**
     * Looks up the license for a component by walking up the parent POM chain in [pomMap].
     * Checks [licenseOverrides] first (by exact "group:artifact" key, then by "group"),
     * then follows `<parent>` links in the POM map.
     * No project access — only reads from the pre-built map of POM files.
     *
     * Returns [LicenseLookupResult.Found] on success, [LicenseLookupResult.UnknownName] if the
     * POM declares a license name not registered in [License.byPomName], or
     * [LicenseLookupResult.NotFound] if no license data exists anywhere in the parent chain.
     */
    private fun lookupLicense(
        component: ExternalComponent,
        pomMap: Map<String, PomLicenseUtils.PomInfo>,
    ): LicenseLookupResult {
        val override = licenseOverrides["${component.group}:${component.name}"]
            ?: licenseOverrides[component.group]
        if (override != null) {
            return LicenseLookupResult.Found(ComponentLicenseInfo(override.displayName, override.url))
        }

        // Walk the parent POM chain using generateSequence; the sequence stops naturally when
        // a key is absent from pomMap or the POM has no <parent>, avoiding multiple breaks.
        val startKey = "${component.group}:${component.name}:${component.version}"
        val pomChain = generateSequence(pomMap[startKey] ?: pomMap["${component.group}:${component.name}"]) { info ->
            info.parent?.let { p -> pomMap["${p.groupId}:${p.artifactId}:${p.version}"] ?: pomMap["${p.groupId}:${p.artifactId}"] }
        }.take(MAX_PARENT_POM_DEPTH)
        for (pomInfo in pomChain) {
            val licenseName = pomInfo.licenseName ?: continue
            return License.fromPomName(licenseName)
                ?.let { LicenseLookupResult.Found(ComponentLicenseInfo(it.displayName, it.url)) }
                ?: LicenseLookupResult.UnknownName(licenseName)
        }
        return LicenseLookupResult.NotFound
    }

    private data class ExternalComponent(val group: String, val name: String, val version: String) {
        val coordKey: String get() = "$group:$name"
    }
}


private data class ComponentLicenseInfo(val displayName: String, val url: String?)


private sealed class LicenseLookupResult {
    data class Found(val info: ComponentLicenseInfo) : LicenseLookupResult()
    /** The POM declares a license name that is not registered as an alias in [License]. */
    data class UnknownName(val rawName: String) : LicenseLookupResult()
    /** No license declaration was found in the POM or any parent POM in the chain. */
    object NotFound : LicenseLookupResult()
}


/**
 * License overrides for components whose POM and all parent POMs have no `<licenses>` section.
 *
 * Keys are matched in order of specificity:
 * - `"groupId:artifactId"` — overrides a single artifact
 * - `"groupId"` — overrides all artifacts in the group
 *
 * Add entries here when the build fails with "no license data in their POM or any parent POM".
 */
private val licenseOverrides: Map<String, License> = mapOf(
    // All net.rubygrapefruit:native-platform-* artifacts are Apache 2.0 licensed but do not
    // include <licenses> in their POMs (https://github.com/gradle/native-platform/issues/40)
    "net.rubygrapefruit" to License.Apache2,
)
