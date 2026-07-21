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

plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.xdcl-builtin-ecosystem")
}

description = "The built-in XDCL Groovy ecosystem plugin (groovy-ecosystem): a carrier generated from groovy-ecosystem.xdcl that binds GroovyLibraryReaction and ships the plugin-stratum defaults (main/test sources, groovyVersion). Shipped in the distribution and applied by id; the schema + facades + model it consumes live in the published :xdcl-groovy-ecosystem library."

dependencies {
    api(projects.coreApi)
    api(projects.baseServices) // Named, Describable, capitalize used by the reaction

    // The single source of truth for this ecosystem's schema + facades + model. Pure CARRIER (only
    // groovy-ecosystem.xdcl, no local .xdsl): xdclCodegen resolves the .xdcl against the lib's schema
    // via importedSchemaDirectories and generates just the carrier; the reaction compiles against the
    // lib's facades and GroovyLibraryModel. Transitively brings :xdcl-common-ecosystem.
    implementation(projects.xdclGroovyEcosystem)

    // Shared imperative helpers (DependencyScopes/Repositories) for the common HasDependencies/
    // HasRepositories capability traits — used by every ecosystem carrier that wires them.
    implementation(projects.xdclEcosystemSupport)

    // Real task types the GroovyLibraryReaction registers (the Groovy analogue of the JVM carrier):
    implementation(projects.core)          // Copy, project internals
    implementation(projects.languageGroovy) // GroovyCompile
    implementation(projects.languageJvm)   // AbstractCompile source/target compatibility
    implementation(projects.platformJvm)   // org.gradle.jvm.tasks.Jar
    implementation(projects.testingJvm)    // org.gradle.api.tasks.testing.Test
    implementation(projects.testingBase)   // Test report containers
    implementation(projects.reporting)     // DirectoryReport output locations
    implementation(projects.loggingApi)    // project.getLogger()
}
