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
        "StringCaseLocaleUsage", // 1 occurrences
    )
}

dependencies {
    api(project(":logging-api"))
    api(projects.javaLanguageExtensions)
    api(project(":base-services"))
    api(project(":build-operations"))
    api(project(":core-api"))
    api(project(":enterprise-logging"))
    api(project(":file-temp"))
    api(project(":file-collections"))
    api(project(":process-services"))

    api(libs.inject)
    api(libs.jsr305)
    api(libs.nativePlatform)

    implementation(project(":functional"))
    implementation(project(":native"))

    implementation(libs.guava)
    implementation(libs.asm)
    implementation(libs.xmlApis)
    implementation(libs.slf4jApi)

    testImplementation(project(":native"))
    testImplementation(project(":file-collections"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":resources"))
    testImplementation(libs.slf4jApi)
    testImplementation(testFixtures(project(":core")))

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
