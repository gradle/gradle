/*
 * Copyright 2014 the original author or authors.
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

import build.kotlinVersion
plugins {
    gradlebuild.distribution.`api-java`
}

dependencies {
    api(project(":coreApi"))

    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":persistentCache"))
    implementation(project(":baseServicesGroovy"))
    implementation(project(":messaging"))
    implementation(project(":snapshots"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation(library("inject"))
    implementation(library("groovy"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("asm"))

    testFixturesApi(testFixtures(project(":diagnostics")))
    testFixturesApi(testFixtures(project(":core")))
    testFixturesImplementation(project(":internalIntegTesting"))
    testFixturesImplementation(library("guava"))

    testImplementation(project(":processServices"))
    testImplementation(project(":fileCollections"))
    testImplementation(project(":native"))
    testImplementation(project(":resources"))
    testImplementation(testFixtures(project(":coreApi")))

    integTestImplementation(project(":platformBase"))

    testRuntimeOnly(project(":distributionsCore")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributionsCore"))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

classycle {
    excludePatterns.set(listOf(
        "org/gradle/model/internal/core/**",
        "org/gradle/model/internal/inspect/**",
        "org/gradle/api/internal/tasks/**",
        "org/gradle/model/internal/manage/schema/**",
        "org/gradle/model/internal/type/**",
        "org/gradle/api/internal/plugins/*"
    ))
}
