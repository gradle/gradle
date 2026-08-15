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

/**
 * Shared contract for a published built-in XDCL ecosystem schema library (the `:xdcl-*` lib half
 * of each ecosystem). The generated facades extend `org.gradle.api.xdcl.*`, so every such library
 * needs the external `xdclGradleApi` facade base types as `api` (exposed transitively to
 * consumers) — declared here once instead of in every lib build script. Every such library is also
 * served by the distribution's embedded Maven repository (that is what makes it a BUILT-IN
 * ecosystem library), so `gradlebuild.distribution-repository` — and through it
 * `gradlebuild.publish-public-libraries` — is applied here rather than by each module.
 */

import gradlebuild.xdcl.excludeGeneratedXdclSourcesFromChecks

plugins {
    `java-library`
    id("gradlebuild.distribution-repository")
    id("xdcl-gradle-plugin")
}

excludeGeneratedXdclSourcesFromChecks()

// External (org.xdcl) facade base types the generated facades extend; `api` so consumers of the
// published library get them transitively.
dependencies {
    "api"(project.versionCatalogs.named("libs").findLibrary("xdclGradleApi").get())
}

// Opt this library's jar out of the generated Gradle API Kotlin DSL extensions: the generated
// facades (org.gradle.xdcl.ecosystem.*) fall inside the public-API spec and would otherwise grow generated
// public API (see NO_KOTLIN_DSL_EXTENSIONS_MARKER). An empty presence-only marker; purely
// distribution-BUILD metadata, the runtime never reads it.
val noKotlinDslExtensionsMarkerDir = layout.buildDirectory.dir("generated/no-kotlin-dsl-extensions-marker")
val writeNoKotlinDslExtensionsMarker = tasks.register<WriteProperties>("writeNoKotlinDslExtensionsMarker") {
    description = "Marks this schema library's jar as excluded from Gradle API Kotlin DSL extension generation"
    destinationFile = noKotlinDslExtensionsMarkerDir.map { it.file(gradlebuild.packaging.NO_KOTLIN_DSL_EXTENSIONS_MARKER) }
}
sourceSets.main {
    // The marker task provider carries its own task dependency, so `output.dir` wires `builtBy`
    // automatically — no `mapOf("builtBy" to …)`.
    output.dir(writeNoKotlinDslExtensionsMarker.map { noKotlinDslExtensionsMarkerDir.get() })
}
