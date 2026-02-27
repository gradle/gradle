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

import gradlebuild.packaging.tasks.CheckExternalDependenciesInLicenseTask
import gradlebuild.packaging.tasks.UpdateLicenseDependenciesTask
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

val distributionDependencies = extensions.getByType(VersionCatalogsExtension::class).named("libs")
val catalogExternalModules = distributionDependencies.libraryAliases.mapNotNull { alias ->
    val dependency = distributionDependencies.findLibrary(alias).orElse(null)?.get() ?: return@mapNotNull null
    val version = dependency.versionConstraint.requiredVersion
        .ifBlank { dependency.versionConstraint.strictVersion }
        .ifBlank { null }
        ?: return@mapNotNull null
    "${dependency.module.group}:${dependency.module.name}:$version"
}
val catalogExternalModulesGa = catalogExternalModules.map { it.substringBeforeLast(':') }
val rootLicensePath = rootDir.resolve("LICENSE")
val licenseFieldSeparator = "\u001F"
val pomLicenseByCoordinates = mutableMapOf<String, PomLicense?>()

val catalogPomLicenseByModule = mutableMapOf<String, String>()
val catalogModulesMissingPomLicense = mutableListOf<String>()
catalogExternalModules.forEach { moduleNotation ->
    val parts = moduleNotation.split(":")
    check(parts.size == 3) { "Invalid module notation: $moduleNotation" }
    val group = parts[0]
    val name = parts[1]
    val version = parts[2]
    val ga = "$group:$name"
    val pomLicense = resolveLicenseFromPom(ModuleCoordinates(group, name, version))
    if (pomLicense == null) {
        catalogModulesMissingPomLicense += ga
    } else {
        val encoded = pomLicense.title + licenseFieldSeparator + (pomLicense.url ?: "")
        catalogPomLicenseByModule[ga] = encoded
    }
}

val checkExternalDependenciesAreListedInLicense by tasks.registering(CheckExternalDependenciesInLicenseTask::class) {
    description = "Verifies that all external distribution dependencies are listed in LICENSE."
    externalModulesGa.set(catalogExternalModulesGa)
    licenseFile.set(rootLicensePath)
}

val updateLicenseDependencies by tasks.registering(UpdateLicenseDependenciesTask::class) {
    description = "Updates LICENSE external dependency entries in-place using the existing license section format."
    externalModules.set(catalogExternalModules)
    licenseFile.set(rootLicensePath)
    pomLicenseByModule.set(catalogPomLicenseByModule)
    modulesMissingPomLicense.set(catalogModulesMissingPomLicense.sorted())
}

tasks.named("check") {
    dependsOn(checkExternalDependenciesAreListedInLicense)
}

data class PomLicense(val title: String, val url: String?)
data class ModuleCoordinates(val group: String, val name: String, val version: String) {
    val gav: String
        get() = "$group:$name:$version"
}

fun resolvePomFile(dependencyNotation: String): File {
    val pomDependency = dependencies.create(dependencyNotation)
    val detachedConfiguration = configurations.detachedConfiguration(pomDependency)
    detachedConfiguration.isTransitive = false
    return detachedConfiguration.singleFile
}

fun resolvePomFile(group: String, name: String, version: String): File {
    findPomInGradleCache(group, name, version)?.let { return it }
    return resolvePomFile("$group:$name:$version@pom")
}

fun findPomInGradleCache(group: String, name: String, version: String): File? {
    val baseDir = gradle.gradleUserHomeDir.resolve("caches/modules-2/files-2.1/$group/$name/$version")
    if (!baseDir.isDirectory) {
        return null
    }
    return baseDir.walkTopDown()
        .maxDepth(2)
        .firstOrNull { it.isFile && it.name == "$name-$version.pom" }
}

fun parseLicenseFromPom(pomFile: File): PomLicense? {
    val documentBuilderFactory = DocumentBuilderFactory.newInstance()
    documentBuilderFactory.isNamespaceAware = false
    val document = documentBuilderFactory.newDocumentBuilder().parse(pomFile)
    val licenseNodes = document.getElementsByTagName("license")
    for (index in 0 until licenseNodes.length) {
        val licenseElement = licenseNodes.item(index) as? Element ?: continue
        val name = licenseElement.childText("name") ?: continue
        val url = licenseElement.childText("url")
        return PomLicense(name, url)
    }
    return null
}

fun resolveLicenseFromPom(
    coordinates: ModuleCoordinates,
    visited: MutableSet<String> = mutableSetOf()
): PomLicense? {
    if (!visited.add(coordinates.gav)) {
        return null
    }
    return pomLicenseByCoordinates.getOrPut(coordinates.gav) {
        val pomFile = runCatching {
            resolvePomFile(coordinates.group, coordinates.name, coordinates.version)
        }.getOrNull() ?: return@getOrPut null

        parseLicenseFromPom(pomFile)
            ?: parseParentCoordinatesFromPom(pomFile)?.let { parent ->
                resolveLicenseFromPom(parent, visited)
            }
    }
}

fun parseParentCoordinatesFromPom(pomFile: File): ModuleCoordinates? {
    val documentBuilderFactory = DocumentBuilderFactory.newInstance()
    documentBuilderFactory.isNamespaceAware = false
    val document = documentBuilderFactory.newDocumentBuilder().parse(pomFile)
    val parentNode = document.getElementsByTagName("parent").item(0) as? Element ?: return null
    val group = parentNode.childText("groupId") ?: return null
    val artifact = parentNode.childText("artifactId") ?: return null
    val version = parentNode.childText("version") ?: return null
    return ModuleCoordinates(group, artifact, version)
}

fun Element.childText(tagName: String): String? {
    val node = getElementsByTagName(tagName).item(0) ?: return null
    val value = node.textContent.trim()
    return value.takeIf { it.isNotEmpty() }
}
