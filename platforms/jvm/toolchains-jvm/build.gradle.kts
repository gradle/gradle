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
    id("gradlebuild.distribution.api-kotlin")
}

description = "Adds support for using JVM toolchains in projects"

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.serviceProvider)
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagement)
    api(projects.enterpriseOperations)
    api(projects.enterpriseLogging)
    api(projects.fileOperations)
    api(projects.jvmServices)
    api(projects.native)
    api(projects.persistentCache)
    api(projects.platformBase)
    api(projects.processServices)
    api(projects.resources)
    api(projects.toolchainsJvmShared)

    api(libs.kotlinStdlib)
    api(libs.inject)

    implementation(projects.baseDiagnostics)
    implementation(projects.fileTemp)
    implementation(projects.modelCore)

    implementation(libs.guava)
    implementation(libs.slf4jApi)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.logging))
    testImplementation(testFixtures(projects.toolchainsJvmShared))

    testFixturesImplementation(projects.native)

    testRuntimeOnly(projects.distributionsCore) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }

    integTestImplementation(libs.slf4jApi)

    integTestDistributionRuntimeOnly(projects.distributionsJvm)
    crossVersionTestDistributionRuntimeOnly(projects.distributionsJvm)
    crossVersionTestImplementation(testFixtures(projects.toolchainsJvmShared))
}

packageCycles {
    // Needed for the factory methods in the interface
    excludePatterns.add("org/gradle/jvm/toolchain/**")
}

integTest.usesJavadocCodeSnippets.set(true)
tasks.isolatedProjectsIntegTest {
    enabled = false
}
