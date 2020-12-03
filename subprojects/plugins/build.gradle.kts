/*
 * Copyright 2010 the original author or authors.
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

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":file-collections"))
    implementation(project(":persistent-cache"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":workers"))
    implementation(project(":dependency-management"))
    implementation(project(":reporting"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-jvm"))
    implementation(project(":language-java"))
    implementation(project(":language-groovy"))
    implementation(project(":diagnostics"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))
    implementation(project(":snapshots"))
    implementation(project(":execution")) {
        because("We need it for BuildOutputCleanupRegistry")
    }

    implementation(libs.slf4jApi)
    implementation(libs.groovy)
    implementation(libs.ant)
    implementation(libs.asm)
    implementation(libs.guava)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.inject)

    testImplementation(project(":messaging"))
    testImplementation(project(":native"))
    testImplementation(project(":resources"))
    testImplementation(libs.gson) {
        because("for unknown reason (bug in the Groovy/Spock compiler?) requires it to be present to use the Gradle Module Metadata test fixtures")
    }
    testImplementation(libs.jsoup)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":resources-http")))
    testImplementation(testFixtures(project(":platform-native")))
    testImplementation(testFixtures(project(":jvm-services")))
    testImplementation(testFixtures(project(":language-jvm")))
    testImplementation(testFixtures(project(":language-java")))
    testImplementation(testFixtures(project(":language-groovy")))
    testImplementation(testFixtures(project(":diagnostics")))

    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation(project(":base-services-groovy"))
    testFixturesImplementation(project(":file-collections"))
    testFixturesImplementation(project(":language-jvm"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(project(":process-services"))
    testFixturesImplementation(project(":resources"))
    testFixturesImplementation(libs.guava)

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
    ignoreDeprecations() // uses deprecated software model types
}

classycle {
    excludePatterns.set(listOf("org/gradle/**"))
}

integTest.usesSamples.set(true)
testFilesCleanup.reportOnly.set(true)
