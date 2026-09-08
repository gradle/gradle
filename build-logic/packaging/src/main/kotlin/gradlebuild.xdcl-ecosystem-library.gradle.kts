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
 * needs the XDCL Gradle API facade base types on its API — declared here once instead of in every
 * lib build script (see the dependencies block for why it is `compileOnlyApi` on the republishing
 * module). Every such library is also served by the distribution's embedded Maven repository (that
 * is what makes it a BUILT-IN ecosystem library), so `gradlebuild.distribution-repository` — and
 * through it `gradlebuild.publish-public-libraries` — is applied here rather than by each module.
 */

import gradlebuild.xdcl.excludeGeneratedXdclSourcesFromChecks
import gradlebuild.xdcl.publishGeneratedXdclSources

plugins {
    `java-library`
    id("gradlebuild.distribution-repository")
    id("xdcl-gradle-plugin")
}

excludeGeneratedXdclSourcesFromChecks()
publishGeneratedXdclSources()

// The facade base types the generated facades extend (org.gradle.api.xdcl.*), taken from the
// gradle/gradle module that REPUBLISHES the composite-sourced org.xdcl jar as
// `org.xdcl:xdcl-gradle-api` at the Gradle distribution's version — not from the org.xdcl module
// itself. Publishing records a project dependency under the target's PUBLICATION coordinates, so
// this is what makes the published metadata of every ecosystem library name the API module at the
// distribution version (the version the promotion build publishes it under), instead of the
// included build's own `0.1.0-SNAPSHOT`, which no external repository ever serves.
//
// `compileOnlyApi`, not `api`: the dependency is part of the published API (module-metadata API
// variant + POM `compile` scope), so an authoring build compiling against a published library
// resolves the API module like any other dependency — but it is NOT part of the runtime variant.
// At runtime the distribution's own `lib/` copy of the API classes is the one that loads (parent-
// first, `org.gradle` prefix), and keeping the republished jar out of the runtime closure is also
// what keeps a SECOND copy of those classes out of the distribution image, which bundles these
// libraries' runtime closure under lib/plugins.
dependencies {
    "compileOnlyApi"(project(":xdcl-gradle-api-publication"))
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
