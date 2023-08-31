/*
 * Copyright 2023 the original author or authors.
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
    id("gradlebuild.distribution.api-java")
}

description = "The JVM bytecode instrumentation utilities reused across the codebase"

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":base-annotations"))
    implementation(project(":base-common-utils"))

    implementation(libs.asm)
    implementation(libs.jsr305)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = null
    sourceCompatibility = "8"
    targetCompatibility = "8"
}
