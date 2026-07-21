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
    id("gradlebuild.publish-public-libraries")
    id("xdcl-gradle-plugin")
    id("gradlebuild.xdcl-ecosystem-library")
}

description = "Built-in XDCL instrumentation ecosystem schema: the instrument { } extension per source set + generated facades. Depends on the JVM ecosystem schema it augments. Published for plugin authors and shipped in the distribution (prototype)"

// A schema-only ecosystem library: instrumentation.xdsl (packed under META-INF/xdcl/ by
// xdcl-gradle-plugin) and the facades generated from it. The reaction that activates it (ASM bytecode
// rewrite) lives in the sibling carrier :xdcl-jvm-instrumentation-plugin. Publishable because it
// carries no internal-Gradle dependency.
dependencies {
    // instrumentation.xdsl `import`s org.gradle.demos.java.dsl (extends HasJavaSources), so the JVM
    // ecosystem schema+facades are part of this library's API. `api` so consumers importing the
    // instrumentation schema also see the JVM facades, and so xdclCodegen resolves the import.
    api(projects.xdclJvmEcosystem)
}
