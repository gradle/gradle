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
 * Shared dependency contract for a published built-in XDCL ecosystem schema library (the `:xdcl-*`
 * lib half of each ecosystem). The generated facades extend `org.gradle.api.xdcl.*`, so every such
 * library needs the external `xdclGradleApi` facade base types as `api` (exposed transitively to
 * consumers) — declared here once instead of in every lib build script. Applied alongside
 * `gradlebuild.publish-public-libraries` + `xdcl-gradle-plugin`.
 */

import gradlebuild.xdcl.excludeGeneratedXdclSourcesFromChecks

plugins {
    `java-library`
    id("xdcl-gradle-plugin")
}

excludeGeneratedXdclSourcesFromChecks()

// External (org.xdcl) facade base types the generated facades extend; `api` so consumers of the
// published library get them transitively.
dependencies {
    "api"(project.versionCatalogs.named("libs").findLibrary("xdclGradleApi").get())
}
