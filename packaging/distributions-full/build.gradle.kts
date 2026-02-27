import gradlebuild.basics.BuildEnvironmentExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    id("gradlebuild.distribution.packaging")
    id("gradlebuild.verify-build-environment")
    id("gradlebuild.install")
}

description = "The collector project for the entirety of the Gradle distribution"

dependencies {
    coreRuntimeOnly(platform(projects.corePlatform))

    agentsRuntimeOnly(projects.instrumentationAgent)

    pluginsRuntimeOnly(platform(projects.distributionsPublishing))
    pluginsRuntimeOnly(platform(projects.distributionsJvm))
    pluginsRuntimeOnly(platform(projects.distributionsNative))

    pluginsRuntimeOnly(projects.pluginDevelopment)
    pluginsRuntimeOnly(projects.buildConfiguration)
    pluginsRuntimeOnly(projects.buildInit)
    pluginsRuntimeOnly(projects.wrapperMain) {
        because("Need to include the wrapper source in the distribution")
    }
    pluginsRuntimeOnly(projects.buildProfile)
    pluginsRuntimeOnly(projects.antlr)
    pluginsRuntimeOnly(projects.enterprise)
    pluginsRuntimeOnly(projects.unitTestFixtures)
}

// This is required for the separate promotion build and should be adjusted there in the future
val buildEnvironmentExtension = extensions.getByType(BuildEnvironmentExtension::class)
tasks.register<Copy>("copyDistributionsToRootBuild") {
    dependsOn("buildDists")
    from(layout.buildDirectory.dir("distributions"))
    into(buildEnvironmentExtension.rootProjectBuildDir.dir("distributions"))
}

data class ExternalModule(val group: String, val name: String, val version: String) {
    val ga: String
        get() = "$group:$name"
}

data class ManagedLicense(val title: String, val url: String? = null)

data class LicenseSectionRange(
    val title: String,
    val delimiterIndex: Int,
    val modulesStart: Int,
    val modulesEnd: Int
)

val licenseSeparator = "------------------------------------------------------------------------------"
val licenseListHeader = "Licenses for included components:"
val moduleLineRegex = Regex("""^[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+$""")
val generatedLicenseStartMarker = "=== BEGIN GENERATED DISTRIBUTION DEPENDENCY LICENSES ==="
val generatedLicenseEndMarker = "=== END GENERATED DISTRIBUTION DEPENDENCY LICENSES ==="

val managedLicenses = listOf(
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

fun resolveDistributionExternalModules(): Set<ExternalModule> {
    val distributionDependencies = extensions.getByType(VersionCatalogsExtension::class).named("libs")
    return distributionDependencies.libraryAliases.mapNotNull { alias ->
        val dependency = distributionDependencies.findLibrary(alias).orElse(null)?.get() ?: return@mapNotNull null
        val version = dependency.versionConstraint.requiredVersion
            .ifBlank { dependency.versionConstraint.strictVersion }
            .ifBlank { null }
            ?: return@mapNotNull null
        ExternalModule(dependency.module.group, dependency.module.name, version)
    }
        .toSet()
}

fun listedLicenseModules(lines: List<String>): Set<String> =
    lines.map(String::trim)
        .filter { moduleLineRegex.matches(it) }
        .toSet()

fun sectionModules(lines: List<String>, section: LicenseSectionRange): Set<String> =
    lines.subList(section.modulesStart, section.modulesEnd)
        .map(String::trim)
        .filter { moduleLineRegex.matches(it) }
        .toSet()

fun managedLicenseByTitle(title: String): ManagedLicense =
    managedLicenses.first { it.title == title }

fun managedLicenseForSection(section: LicenseSectionRange): ManagedLicense =
    managedLicenseByTitle(section.title)

fun existingModulesByManagedLicense(lines: List<String>): Map<ManagedLicense, Set<String>> =
    managedLicenses.mapNotNull { license ->
        findSection(lines, license.title)?.let { section ->
            license to sectionModules(lines, section)
        }
    }.toMap()

fun resolvePomFile(module: ExternalModule): File? {
    val pomNotation = "${module.group}:${module.name}:${module.version}@pom"
    val detached = configurations.detachedConfiguration(dependencies.create(pomNotation)).apply {
        isTransitive = false
        resolutionStrategy.disableDependencyVerification()
    }
    return try {
        detached.resolve().firstOrNull()
    } catch (_: Exception) {
        null
    }
}

fun normalizeLicense(name: String?, url: String?): ManagedLicense? {
    val normalizedName = name?.trim().orEmpty().lowercase()
    val normalizedUrl = url?.trim().orEmpty().lowercase()
    return when {
        "apache" in normalizedName || "apache.org/licenses" in normalizedUrl ->
            managedLicenses.first { it.title == "Apache 2.0" }
        (("bsd" in normalizedName && "3" in normalizedName) || "3-clause" in normalizedName || "bsd-3-clause" in normalizedUrl) ->
            managedLicenses.first { it.title == "3-Clause BSD" }
        "mit" in normalizedName || normalizedUrl.endsWith("/mit") ->
            managedLicenses.first { it.title == "MIT" }
        "cddl" in normalizedName || "cddl-1.0" in normalizedUrl ->
            managedLicenses.first { it.title == "CDDL" }
        ("eclipse public license" in normalizedName && "1.0" in normalizedName) || "epl-1.0" in normalizedUrl ->
            managedLicenses.first { it.title == "Eclipse Public License 1.0" }
        ("eclipse public license" in normalizedName && "2.0" in normalizedName) || "epl-2.0" in normalizedUrl ->
            managedLicenses.first { it.title == "Eclipse Public License 2.0" }
        "eclipse distribution license" in normalizedName || "edl-v10" in normalizedUrl ->
            managedLicenses.first { it.title == "Eclipse Distribution License 1.0" }
        ("lgpl" in normalizedName && "2.1" in normalizedName) || "lgpl-2.1" in normalizedUrl ->
            managedLicenses.first { it.title == "LGPL 2.1" }
        "bsd-style" in normalizedName ->
            managedLicenses.first { it.title == "BSD-style" }
        ("mozilla public license" in normalizedName && "2.0" in normalizedName) || "mozilla.org/en-us/mpl/2.0" in normalizedUrl ->
            managedLicenses.first { it.title == "Mozilla Public License 2.0" }
        else -> null
    }
}

fun licenseForModule(module: ExternalModule): ManagedLicense? {
    val pom = resolvePomFile(module) ?: return null
    return try {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom)
        val licenses = document.getElementsByTagName("license")
        if (licenses.length == 0) {
            null
        } else {
            val node = licenses.item(0)
            val children = node.childNodes
            var name: String? = null
            var url: String? = null
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child.nodeName == "name") name = child.textContent
                if (child.nodeName == "url") url = child.textContent
            }
            normalizeLicense(name, url)
        }
    } catch (_: Exception) {
        null
    }
}

fun fallbackLicenseForModule(module: ExternalModule): ManagedLicense? {
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

fun findSection(lines: List<String>, title: String): LicenseSectionRange? {
    for (i in 0 until lines.lastIndex) {
        if (lines[i] == licenseSeparator && lines[i + 1].trim() == title) {
            var cursor = i + 2
            if (cursor < lines.size && lines[cursor].startsWith("http")) cursor++
            if (cursor < lines.size && lines[cursor].isBlank()) cursor++
            val modulesStart = cursor
            var modulesEnd = modulesStart
            while (modulesEnd < lines.size && (lines[modulesEnd].isBlank() || moduleLineRegex.matches(lines[modulesEnd].trim()))) {
                modulesEnd++
            }
            return LicenseSectionRange(title, i, modulesStart, modulesEnd)
        }
    }
    return null
}

fun legacyLicenseAssignments(lines: List<String>): Map<String, ManagedLicense> =
    managedLicenses.mapNotNull { license ->
        findSection(lines, license.title)?.let { section ->
            val modules = lines.subList(section.modulesStart, section.modulesEnd)
                .map(String::trim)
                .filter { moduleLineRegex.matches(it) }
            license to modules
        }
    }.flatMap { (license, modules) ->
        modules.map { module -> module to license }
    }.toMap()

fun replaceModuleRange(lines: MutableList<String>, section: LicenseSectionRange, modules: List<String>) {
    lines.subList(section.modulesStart, section.modulesEnd).clear()
    val insertion = modules + listOf("")
    lines.addAll(section.modulesStart, insertion)
}

fun renderManagedSection(license: ManagedLicense, modules: List<String>): List<String> {
    val result = mutableListOf<String>()
    result += licenseSeparator
    result += license.title
    license.url?.let { result += it }
    result += ""
    result += modules
    result += ""
    return result
}

fun removeLegacyGeneratedBlock(content: String): String {
    val start = content.indexOf(generatedLicenseStartMarker)
    val end = content.indexOf(generatedLicenseEndMarker)
    return if (start >= 0 && end > start) {
        val endExclusive = end + generatedLicenseEndMarker.length
        (content.substring(0, start) + content.substring(endExclusive)).replace(Regex("\n{3,}"), "\n\n").trimEnd() + "\n"
    } else {
        content
    }
}

val checkExternalDependenciesAreListedInLicense by tasks.registering {
    description = "Verifies that all external distribution dependencies are listed in LICENSE."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    notCompatibleWithConfigurationCache("Reads version catalog during task execution.")

    doLast {
        val externalModules = resolveDistributionExternalModules().map { it.ga }.toSet()
        val cleanedLicense = removeLegacyGeneratedBlock(rootDir.resolve("LICENSE").readText())
        val missingModules = (externalModules - listedLicenseModules(cleanedLicense.lines())).sorted()
        if (missingModules.isNotEmpty()) {
            error(
                """
                The following external dependencies are missing from LICENSE:
                ${missingModules.joinToString(separator = "\n")}

                Run :distributions-full:updateLicenseDependencies to update LICENSE automatically.
                """.trimIndent()
            )
        }
    }
}

val updateLicenseDependencies by tasks.registering {
    description = "Updates LICENSE external dependency entries in-place using the existing license section format."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    notCompatibleWithConfigurationCache("Reads version catalog and resolves POM metadata during task execution.")

    doLast {
        val licenseFile = rootDir.resolve("LICENSE")
        val cleanedContent = removeLegacyGeneratedBlock(licenseFile.readText())
        val lines = cleanedContent.lines().toMutableList()
        val headerIndex = lines.indexOf(licenseListHeader)
        require(headerIndex >= 0) { "Could not find '$licenseListHeader' in LICENSE." }
        val legacyAssignments = legacyLicenseAssignments(lines)
        val existingModulesByLicense = existingModulesByManagedLicense(lines)

        val modulesByLicense = resolveDistributionExternalModules().groupBy { module ->
            licenseForModule(module) ?: legacyAssignments[module.ga] ?: fallbackLicenseForModule(module)
        }

        val unknownModules = modulesByLicense[null].orEmpty().map { it.ga }.sorted()
        if (unknownModules.isNotEmpty()) {
            error(
                """
                Could not determine a managed LICENSE section for:
                ${unknownModules.joinToString("\n")}

                Please add mapping support in updateLicenseDependencies for these modules' license metadata.
                """.trimIndent()
            )
        }

        val managedModules = modulesByLicense.filterKeys { it != null }
            .mapKeys { entry -> entry.key!! }
            .mapValues { (_, modules) -> modules.map { it.ga }.toSet() }

        val sections = managedLicenses.mapNotNull { license -> findSection(lines, license.title) }
        sections.sortedByDescending { it.modulesStart }.forEach { section ->
            val managedLicense = managedLicenseForSection(section)
            val merged = (existingModulesByLicense[managedLicense].orEmpty() + managedModules[managedLicense].orEmpty()).sorted()
            replaceModuleRange(lines, section, merged)
        }

        val existingTitles = sections.map { it.title }.toSet()
        val missingSections = managedLicenses.filter { it.title !in existingTitles && !managedModules[it].isNullOrEmpty() }
        if (missingSections.isNotEmpty()) {
            val insertionIndex = lines.size
            val rendered = missingSections.flatMap { renderManagedSection(it, managedModules[it].orEmpty().sorted()) }
            lines.addAll(insertionIndex, rendered)
        }

        licenseFile.writeText(lines.joinToString("\n").trimEnd() + "\n")
    }
}

tasks.named("check") {
    dependsOn(checkExternalDependenciesAreListedInLicense)
}
