/*
 * Copyright 2018 the original author or authors.
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
import gradlebuild.cleanup.WhenNotEmpty

plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":file-collections"))
    implementation(project(":resources"))
    implementation(project(":base-services-groovy"))
    implementation(project(":dependency-management"))
    implementation(project(":plugins"))
    implementation(project(":plugin-use"))
    implementation(project(":publish"))

    implementation(libs.slf4jApi)
    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)
    implementation(libs.ant)
    implementation(libs.ivy)
    implementation(libs.maven3)
    implementation(libs.pmavenCommon)
    implementation(libs.pmavenGroovy)
    implementation(libs.maven3WagonFile)
    implementation(libs.maven3WagonHttp)
    implementation(libs.plexusContainer)
    implementation(libs.aetherConnector)

    testImplementation(project(":native"))
    testImplementation(project(":process-services"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":resources-http"))
    testImplementation(libs.xmlunit)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":model-core")))
    testImplementation(testFixtures(project(":dependency-management")))

    integTestImplementation(project(":ear"))

    testFixturesApi(project(":base-services")) {
        because("Test fixtures export the Action class")
    }
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(project(":dependency-management"))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-core"))
}

strictCompile {
    ignoreDeprecations() // old 'maven' publishing mechanism: types are deprecated
    ignoreRawTypes() // old 'maven' publishing mechanism: raw types used in public API
}

classycle {
    excludePatterns.set(listOf(
        "org/gradle/api/publication/maven/internal/**",
        "org/gradle/api/artifacts/maven/**"))
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}

integTest.usesSamples.set(true)
