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

import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.tasks.javadoc.Javadoc

plugins {
    id("gradlebuild.distribution.implementation-java")
    id("xdcl-gradle-plugin")
    id("gradlebuild.xdcl-builtin-ecosystem")
}

description = "The built-in XDCL JVM ecosystem plugin (java-ecosystem): a carrier generated from java-ecosystem.xdcl that binds JavaLibraryReaction and ships the plugin-stratum defaults. Shipped in the distribution and applied by id; the schema + facades it consumes live in the published :xdcl-jvm-ecosystem library."

// xdcl-gradle-plugin generates the carrier from src/main/xdcl/java-ecosystem.xdcl and wires the
// generated facade sources into main. They are build artifacts, so the style/header/javadoc gates
// that apply to authored source must not see them.
tasks.withType<Checkstyle>().configureEach {
    exclude { it.file.absolutePath.contains("/generated/xdcl/") }
}
tasks.withType<Javadoc>().configureEach {
    exclude { it.file.absolutePath.contains("/generated/xdcl/") }
}

// The manifest the provider reads (keyed by plugin id) to PULL this ecosystem's schema module via
// ModuleRegistry when the plugin is applied — the built-in-distribution contribution channel. No
// hand-written Plugin<Settings> and no ModuleRegistry code here: the carrier is Gary-style generated,
// the module-layout knowledge lives only in this build + the provider.
xdclBuiltinEcosystem {
    pluginId = "java-ecosystem"
    // No schema-module list: the convention defaults to this plugin's own module, and the provider
    // walks its dependency closure (ModuleRegistry) to pick up every schema-carrying jar — the
    // plugin's own schema here, and transitively any schema modules a future ecosystem depends on.
}

dependencies {
    api(projects.coreApi)
    api(projects.baseServices) // Named, Describable, capitalize used by the reaction/model

    // The single source of truth for the schema + facades. This is a PURE CARRIER module (only
    // java-ecosystem.xdcl, no local .xdsl): xdclCodegen resolves the `.xdcl`'s references against the
    // lib's schema via importedSchemaDirectories (runtimeClasspath) and generates just the carrier,
    // and the reaction compiles against the lib's facades (JavaLibrary, JavaSource). At runtime the
    // provider walks this module's dependency closure and finds the lib jar's schema. No duplication.
    implementation(projects.xdclJvmEcosystem)

    // Shared imperative helpers (DependencyScopes/Repositories) for the common HasDependencies/
    // HasRepositories capability traits — used by every ecosystem carrier that wires them.
    implementation(projects.xdclEcosystemSupport)

    // Real task types the JavaLibraryReaction registers:
    implementation(projects.core)          // Copy, project internals
    implementation(projects.languageJava)  // JavaCompile
    implementation(projects.languageJvm)   // AbstractCompile source/target compatibility
    implementation(projects.platformJvm)   // org.gradle.jvm.tasks.Jar
    implementation(projects.testingJvm)    // org.gradle.api.tasks.testing.Test
    implementation(projects.testingBase)   // Test report containers
    implementation(projects.reporting)     // DirectoryReport output locations
    implementation(projects.loggingApi)    // project.getLogger()
}
