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

import gradlebuild.xdcl.GenerateXdclBuiltinEcosystemManifests
import gradlebuild.xdcl.XdclBuiltinEcosystemExtension
import gradlebuild.xdcl.excludeGeneratedXdclSourcesFromChecks

/**
 * Packs the built-in-ecosystem manifests into a distribution ecosystem plugin jar. For every plugin
 * carrier under `src/main/xdcl/` (an `.xdcl` with a top-level `plugin { }` block) it emits a
 * `META-INF/xdcl-builtin-ecosystem/<plugin-id>.properties` naming the distribution schema module(s)
 * the runtime glue resolves (via `ModuleRegistry`) and contributes to the settings registry. The
 * plugin id is the carrier's file name, so a module declares no ids here.
 */

plugins {
    java
    id("xdcl-gradle-plugin")
}

excludeGeneratedXdclSourcesFromChecks()

// Every built-in ecosystem carrier's generated carrier + its reactions reference org.gradle.api.xdcl.*
// (Reaction/ReactionScope/BindReaction/PluginDefaults). It is Gradle API in the distribution at runtime,
// so add it compile-only here — once, instead of in every carrier build script — and the plugin jar
// does not bundle a second copy (classloader identity).
dependencies {
    "compileOnly"(project.versionCatalogs.named("libs").findLibrary("xdclGradleApi").get())
}

val xdclBuiltinEcosystem = extensions.create<XdclBuiltinEcosystemExtension>("xdclBuiltinEcosystem")
xdclBuiltinEcosystem.schemaModules.convention(listOf("gradle-${project.name}"))

val generatedDir = layout.buildDirectory.dir("generated/xdcl-builtin-ecosystem")

val writeXdclBuiltinEcosystemManifests = tasks.register<GenerateXdclBuiltinEcosystemManifests>("writeXdclBuiltinEcosystemManifests") {
    description = "Generates the built-in-ecosystem manifest resource for each plugin carrier in the module"
    group = "build"
    carrierFiles.from(project.fileTree("src/main/xdcl") { include("*.xdcl") })
    schemaModules = xdclBuiltinEcosystem.schemaModules
    outputDir = generatedDir
}

sourceSets.main {
    output.dir(mapOf("builtBy" to writeXdclBuiltinEcosystemManifests), generatedDir)
}
