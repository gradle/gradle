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

package gradlebuild

import org.gradle.api.initialization.Settings

/**
 * Defines a top-level architecture module.
 */
fun Settings.module(moduleName: String, moduleConfiguration: ArchitectureModuleBuilder.() -> Unit) {
    val module = ArchitectureModuleBuilder(moduleName, this)
    extensions.findByType(ProjectStructure::class.java)!!.architectureElements.add(module)
    module.moduleConfiguration()
}

/**
 * Defines a platform.
 */
fun Settings.platform(platformName: String, platformConfiguration: PlatformBuilder.() -> Unit): PlatformBuilder {
    val platform = PlatformBuilder(platformName, this)
    extensions.findByType(ProjectStructure::class.java)!!.architectureElements.add(platform)
    platform.platformConfiguration()
    return platform
}

/**
 * Defines the packaging module, for project helping package Gradle.
 */
fun Settings.packaging(moduleConfiguration: ProjectScope.() -> Unit) =
    ProjectScope("packaging", this).moduleConfiguration()

/**
 * Defines the testing module, for project helping test Gradle.
 */
fun Settings.testing(moduleConfiguration: ProjectScope.() -> Unit) =
    ProjectScope("testing", this).moduleConfiguration()

/**
 * Defines a bucket of unassigned projects.
 */
fun Settings.unassigned(moduleConfiguration: ProjectScope.() -> Unit) =
    ProjectScope("subprojects", this).moduleConfiguration()

