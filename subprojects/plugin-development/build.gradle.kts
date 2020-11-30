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
    implementation(project(":process-services"))
    implementation(project(":files"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":execution"))
    implementation(project(":core"))
    implementation(project(":dependency-management"))
    implementation(project(":maven"))
    implementation(project(":ivy"))
    implementation(project(":platform-jvm"))
    implementation(project(":reporting"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))
    implementation(project(":plugins"))
    implementation(project(":plugin-use"))
    implementation(project(":publish"))
    implementation(project(":messaging"))
    implementation(project(":workers"))
    implementation(project(":model-groovy"))
    implementation(project(":resources"))

    implementation(libs.slf4jApi)
    implementation(libs.groovy)
    implementation(libs.commonsIo)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.asm)

    testImplementation(project(":file-collections"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":logging")))

    integTestImplementation(project(":base-services-groovy"))
    integTestImplementation(libs.jetbrainsAnnotations)

    integTestLocalRepository(project(":tooling-api")) {
        because("Required by GradleImplDepsCompatibilityIntegrationTest")
    }

    testRuntimeOnly(project(":distributions-basics")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-basics"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-basics"))
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}

integTest.usesSamples.set(true)
