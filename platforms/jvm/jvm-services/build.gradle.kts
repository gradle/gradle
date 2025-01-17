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

plugins {
    id("gradlebuild.distribution.api-java")
}

description = "JVM invocation and inspection abstractions"

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 2 occurrences
    )
}

dependencies {
    api(projects.loggingApi)
    api(projects.stdlibJavaExtensions)
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.coreApi)
    api(projects.enterpriseLogging)
    api(projects.fileTemp)
    api(projects.fileCollections)
    api(projects.processServices)
    api(projects.persistentCache)

    api(libs.inject)
    api(libs.jsr305)
    api(libs.nativePlatform)

    implementation(projects.functional)
    implementation(projects.native)
    implementation(projects.serialization)

    implementation(libs.guava)
    implementation(libs.asm)
    implementation(libs.xmlApis)
    implementation(libs.slf4jApi)

    testImplementation(projects.native)
    testImplementation(projects.fileCollections)
    testImplementation(projects.snapshots)
    testImplementation(projects.resources)
    testImplementation(libs.slf4jApi)
    testImplementation(testFixtures(projects.core))

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}

packageCycles {
    // Needed for the factory methods in the interface since the implementation is in an internal package
    // which in turn references the interface.
    excludePatterns.add("org/gradle/jvm/toolchain/JavaLanguageVersion**")
}
