/*
 * Copyright 2024 the original author or authors.
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

import gradlebuild.basics.PublicApiVariants
import gradlebuild.configureAsRuntimeJarClasspath
import gradlebuild.packaging.support.includePublicApiAbiStubs
import gradlebuild.packaging.support.publicApiAbiStubs

plugins {
    id("gradlebuild.dependency-modules")
    id("gradlebuild.repositories")
    id("gradlebuild.reproducible-archives")
    id("gradlebuild.module-identity")
}

description = "Produces the public API ABI jar (signature stubs) of the Gradle distribution it is applied to"

// The source of API stubs to assemble into the ABI jar. Consumers populate it differently:
// `:public-api` adds a dependency on the distribution to extract the full API surface from;
// each distribution extends it from its own runtime buckets to extract only its own surface.
val distribution = configurations.dependencyScope("distribution") {
    description = "Dependencies to extract the public Gradle API from"
}
val distributionClasspath = configurations.resolvable("distributionClasspath") {
    extendsFrom(distribution.get())
    configureAsRuntimeJarClasspath(objects)
}

// Named after the runtime module so the module registry finds it as `gradle-public-api-legacy` in every distribution.
tasks.register<Jar>("jarGradleApiLegacy") {
    includePublicApiAbiStubs(publicApiAbiStubs(distributionClasspath))
    archiveBaseName = PublicApiVariants.LEGACY_MODULE_NAME
    destinationDirectory = layout.buildDirectory.dir("public-api/gradle-api-legacy")
}
