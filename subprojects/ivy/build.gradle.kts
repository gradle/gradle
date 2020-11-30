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
    implementation(project(":base-services-groovy")) // for 'Specs'
    implementation(project(":file-collections"))
    implementation(project(":resources"))
    implementation(project(":publish"))
    implementation(project(":plugins")) // for base plugin to get archives conf
    implementation(project(":plugin-use"))
    implementation(project(":dependency-management"))

    implementation(libs.groovy) // for 'Closure' and 'Task.property(String propertyName) throws groovy.lang.MissingPropertyException'
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)
    implementation(libs.ivy)

    testImplementation(project(":native"))
    testImplementation(project(":process-services"))
    testImplementation(project(":snapshots"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":model-core")))
    testImplementation(testFixtures(project(":platform-base")))
    testImplementation(testFixtures(project(":dependency-management")))

    integTestImplementation(project(":ear"))
    integTestImplementation(libs.slf4jApi)

    integTestRuntimeOnly(project(":resources-s3"))
    integTestRuntimeOnly(project(":resources-sftp"))
    integTestRuntimeOnly(project(":api-metadata"))

    testFixturesApi(project(":base-services")) {
        because("Test fixtures export the Action class")
    }
    testFixturesApi(project(":core-api")) {
        because("Test fixtures export the RepositoryHandler class")
    }
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(project(":dependency-management"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(libs.slf4jApi)
    testFixturesImplementation(libs.sshdCore)
    testFixturesImplementation(libs.sshdScp)
    testFixturesImplementation(libs.sshdSftp)

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-core"))
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}

integTest.usesSamples.set(true)
