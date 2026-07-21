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
    id("gradlebuild.xdcl-ecosystem-library")
}

description = "Built-in XDCL checkstyle ecosystem schema: the checkstyle { } extensions (on the JVM library and per source set) + generated facades. Depends on the JVM ecosystem schema it augments. Published for plugin authors and shipped in the distribution (prototype)"

// A schema-only ecosystem library: checkstyle.xdsl (packed under META-INF/xdcl/ by xdcl-gradle-plugin)
// and the facades generated from it. The reactions that activate it live in the sibling carrier
// :xdcl-jvm-checkstyle-plugin. Publishable because it carries no internal-Gradle dependency.
dependencies {
    // checkstyle.xdsl `import`s org.gradle.demos.java.dsl (extends JavaComponent / HasJavaSources) and
    // its facades reference the JVM ones, so the JVM ecosystem schema+facades are part of this library's
    // API. `api` so consumers importing the checkstyle schema also see the JVM facades. Published, so the
    // gate is satisfied; also the source xdclCodegen resolves the import from (importedSchemaDirectories).
    api(projects.xdclJvmEcosystem)
}
