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

val checkExternalDependenciesAreListedInLicense by tasks.registering(CheckExternalDependenciesInLicenseTask::class) {
    description = "Verifies that all external distribution dependencies are listed in LICENSE."
    externalModulesGa.set(catalogExternalModulesGa)
    licenseFile.set(rootLicensePath)
}

val updateLicenseDependencies by tasks.registering(UpdateLicenseDependenciesTask::class) {
    description = "Updates LICENSE external dependency entries in-place using the existing license section format."
    externalModules.set(catalogExternalModules)
    licenseFile.set(rootLicensePath)
}

tasks.named("check") {
    dependsOn(checkExternalDependenciesAreListedInLicense)
}
