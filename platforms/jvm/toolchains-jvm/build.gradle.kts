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

errorprone {
    disabledChecks.addAll(
        "StringCaseLocaleUsage", // 2 occurrences
    )
}

dependencies {
    api(projects.javaLanguageExtensions)
    api(projects.serviceProvider)
    api(project(":base-services"))
    api(project(":build-operations"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":dependency-management"))
    api(project(":diagnostics"))
    api(project(":enterprise-operations"))
    api(project(":enterprise-logging"))
    api(project(":file-collections"))
    api(project(":jvm-services"))
    api(project(":model-core"))
    api(project(":persistent-cache"))
    api(project(":platform-base"))
    api(project(":platform-jvm"))
    api(project(":resources"))
    api(project(":toolchains-jvm-shared"))

    api(libs.kotlinStdlib)
    api(libs.inject)
    api(libs.jsr305)
    api(libs.nativePlatform) {
        because("Required for SystemInfo")
    }

    implementation(project(":logging"))

    implementation(libs.commonsIo)
    implementation(libs.guava)
    implementation(libs.slf4jApi)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":logging")))

    testFixturesImplementation(project(":native"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(libs.commonsCompress)

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }

    integTestImplementation(libs.slf4jApi)

    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

packageCycles {
    // Needed for the factory methods in the interface
    excludePatterns.add("org/gradle/jvm/toolchain/**")
}

integTest.usesJavadocCodeSnippets.set(true)
