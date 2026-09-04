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

package gradlebuild.packaging

/**
 * Jar entry marking a distribution jar as EXCLUDED from the generated Gradle API Kotlin DSL
 * extensions (`gradleApiKotlinExtensions` filters marker-bearing jars off the generation
 * classpath). The generator sweeps every `gradle-*` distribution jar for both halves of its
 * output — plugin-id accessors (from `META-INF/gradle-plugins` descriptors) and API type
 * extensions (over everything the public-API spec admits) — so a jar whose plugin ids or types
 * must not become generated public API opts out by carrying this marker: the XDCL ecosystem
 * carriers (their plugin ids) and schema libraries (their generated facade types).
 *
 * Presence-only and empty; written by `gradlebuild.xdcl-ecosystem-library` and
 * `GenerateXdclCarrierManifests`. Purely distribution-build metadata — the runtime never reads it.
 */
const val NO_KOTLIN_DSL_EXTENSIONS_MARKER = "META-INF/gradle/no-kotlin-dsl-extensions.marker"
