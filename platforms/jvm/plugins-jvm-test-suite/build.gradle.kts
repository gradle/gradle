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

description = "Contains the JVM Test Suite plugin"

errorprone {
    disabledChecks.addAll(
        "OverridesJavaxInjectableMethod", // 1 occurrences
    )
}

dependencies {
    api(projects.serviceLookup)
    api(projects.stdlibJavaExtensions)
    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.languageJvm)
    api(projects.modelCore)
    api(projects.platformJvm)
    api(projects.testingJvm)
    api(projects.testSuitesBase)

    api(libs.inject)

    implementation(projects.logging)
    implementation(projects.pluginsJavaBase)
    implementation(projects.testingBase)

    implementation(libs.guava)
    implementation(libs.jspecify)

    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
