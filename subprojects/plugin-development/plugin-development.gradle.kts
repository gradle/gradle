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

import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
import org.gradle.gradlebuild.test.integrationtests.integrationTestUsesSampleDir

plugins {
    gradlebuild.distribution.`api-java`
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":files"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":execution"))
    implementation(project(":core"))
    implementation(project(":dependencyManagement"))
    implementation(project(":maven"))
    implementation(project(":ivy"))
    implementation(project(":platformJvm"))
    implementation(project(":reporting"))
    implementation(project(":testingBase"))
    implementation(project(":testingJvm"))
    implementation(project(":plugins"))
    implementation(project(":pluginUse"))
    implementation(project(":publish"))
    implementation(project(":messaging"))
    implementation(project(":workers"))
    implementation(project(":modelGroovy"))
    implementation(project(":resources"))

    implementation(library("slf4j_api"))
    implementation(library("groovy"))
    implementation(library("commons_io"))
    implementation(library("guava"))
    implementation(library("inject"))
    implementation(library("asm"))

    testImplementation(project(":fileCollections"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":logging")))

    integTestImplementation(project(":baseServicesGroovy"))
    integTestImplementation(library("jetbrains_annotations"))

    integTestLocalRepository(project(":toolingApi")) {
        because("Required by GradleImplDepsCompatibilityIntegrationTest")
    }

    testRuntimeOnly(project(":distributionsBasics")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributionsBasics"))
    crossVersionTestDistributionRuntimeOnly(project(":distributionsBasics"))
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}

integrationTestUsesSampleDir("subprojects/plugin-development/src/main")
