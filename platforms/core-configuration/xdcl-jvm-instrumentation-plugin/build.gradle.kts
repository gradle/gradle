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

description = "The built-in XDCL instrumentation ecosystem plugin (instrumentation-ecosystem): a carrier generated from instrumentation-ecosystem.xdcl that binds the ASM bytecode-rewrite reaction. Shipped in the distribution and applied by id; the schema + facades it consumes live in the published :xdcl-jvm-instrumentation library."

dependencies {
    api(projects.coreApi)
    api(projects.baseServices)

    // The single source of truth for this ecosystem's schema + facades. Pure CARRIER (only
    // instrumentation-ecosystem.xdcl, no local .xdsl): xdclCodegen resolves the .xdcl against the lib's
    // schema via importedSchemaDirectories and generates just the carrier; the reaction compiles against
    // the lib's facades. Transitively brings the JVM ecosystem lib, whose JavaLibraryModel / JavaClasses /
    // HasJavaSources the InstrumentReaction reads — no dependency on the JVM plugin module.
    implementation(projects.xdclJvmInstrumentation)

    implementation(libs.asm) // ClassReader/ClassWriter/MethodVisitor — the bytecode rewrite in InstrumentClasses
}
