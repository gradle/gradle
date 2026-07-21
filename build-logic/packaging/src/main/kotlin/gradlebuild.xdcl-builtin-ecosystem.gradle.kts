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

import gradlebuild.xdcl.XdclBuiltinEcosystemExtension

/**
 * Packs an xdcl-builtin-ecosystem manifest into a built-in XDCL ecosystem plugin jar: a
 * `META-INF/xdcl-builtin-ecosystem/<pluginId>.properties` resource naming the distribution schema
 * module(s) the runtime glue resolves (via `ModuleRegistry`) and contributes to the settings
 * registry. Models `gradlebuild.api-metadata` (WriteProperties -> output.dir -> jar).
 */

plugins {
    java
}

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

val writeXdclBuiltinEcosystemManifest = tasks.register<WriteProperties>("writeXdclBuiltinEcosystemManifest") {
    destinationFile = xdclBuiltinEcosystem.pluginId.flatMap { id ->
        generatedDir.map { it.file("META-INF/xdcl-builtin-ecosystem/$id.properties") }
    }
    property("schemaModules", xdclBuiltinEcosystem.schemaModules.map { it.joinToString(",") })
}

sourceSets.main {
    output.dir(mapOf("builtBy" to writeXdclBuiltinEcosystemManifest), generatedDir)
}
