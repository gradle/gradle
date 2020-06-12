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
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":baseServicesGroovy")) // for 'Specs'
    implementation(project(":fileCollections"))
    implementation(project(":resources"))
    implementation(project(":publish"))
    implementation(project(":plugins")) // for base plugin to get archives conf
    implementation(project(":pluginUse"))
    implementation(project(":dependencyManagement"))

    implementation(library("groovy")) // for 'Closure' and 'Task.property(String propertyName) throws groovy.lang.MissingPropertyException'
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("inject"))
    implementation(library("ivy"))

    testImplementation(project(":native"))
    testImplementation(project(":processServices"))
    testImplementation(project(":snapshots"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":modelCore")))
    testImplementation(testFixtures(project(":platformBase")))
    testImplementation(testFixtures(project(":dependencyManagement")))

    integTestImplementation(project(":ear"))
    integTestImplementation(library("slf4j_api"))
    integTestImplementation(testLibrary("jetty"))

    integTestRuntimeOnly(project(":resourcesS3"))
    integTestRuntimeOnly(project(":resourcesSftp"))
    integTestRuntimeOnly(project(":apiMetadata"))

    testFixturesApi(project(":baseServices")) {
        because("Test fixtures export the Action class")
    }
    testFixturesApi(project(":coreApi")) {
        because("Test fixtures export the RepositoryHandler class")
    }
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(project(":dependencyManagement"))
    testFixturesImplementation(project(":internalIntegTesting"))
    testFixturesImplementation(library("slf4j_api"))
    testLibraries("sshd").forEach { testFixturesImplementation(it) }

    testRuntimeOnly(project(":distributionsCore")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributionsJvm"))
    crossVersionTestDistributionRuntimeOnly(project(":distributionsCore"))
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}


integrationTestUsesSampleDir("subprojects/ivy/src/main")
