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

description = "Declarations to define JVM toolchains shared between launcher and daemon"

dependencies {
    api(libs.inject)
    api(libs.jsr305)

    api(projects.stdlibJavaExtensions)
    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.fileCollections)
    api(projects.jvmServices)
    api(projects.persistentCache)
    api(projects.core)

    implementation(projects.functional)

    implementation(projects.logging)
    implementation(libs.guava)
    implementation(libs.slf4jApi)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)

    testImplementation(testFixtures(projects.core))

    testRuntimeOnly(projects.distributionsJvm) {
        because("ProjectBuilder tests load services from a Gradle distribution.  Toolchain usage requires JVM distribution.")
    }
}

packageCycles {
    excludePatterns.add("org/gradle/jvm/toolchain/**")
}

integTest.usesJavadocCodeSnippets.set(true)
tasks.isolatedProjectsIntegTest {
    enabled = false
}
