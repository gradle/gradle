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
 * compiles against the XDCL Gradle API module — declared here once instead of in every lib build
 * script, and `compileOnly` on purpose (see the dependencies block). Every such library is also
 * served by the distribution's embedded Maven repository (that is what makes it a BUILT-IN
 * ecosystem library), so `gradlebuild.distribution-repository` — and through it
 * `gradlebuild.publish-public-libraries` — is applied here rather than by each module.
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

// The facade base types the generated facades extend (org.gradle.api.xdcl.*) are GRADLE API: the
// :xdcl-api module ships them in the distribution, every derivative API artifact (the public API
// jar, gradleApi(), the Kotlin DSL extensions, the docs) carries them, and any consumer of a
// published ecosystem library — a plugin author's build, or the settings classpath of a build
// applying the ecosystem — runs on a Gradle that already has them. So the dependency is
// `compileOnly`: it appears in NO published metadata, in no variant and at no scope, exactly like
// the rest of the Gradle API these libraries reference.
dependencies {
    "compileOnly"(project(":xdcl-api"))
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
