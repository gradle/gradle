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

description = "Public types for unit testing plugins"

dependencies {
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.buildProcessServices)
    api(projects.concurrent)
    api(projects.core)
    api(projects.coreApi)
    api(projects.persistentCache)
    api(projects.serialization)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.time)

    api(libs.jspecify)

    implementation(projects.buildState)
    implementation(projects.classloaders)
    implementation(projects.daemonServices)
    implementation(projects.fileCollections)
    implementation(projects.fileTemp)
    implementation(projects.instrumentationAgentServices)
    implementation(projects.logging)
    implementation(projects.loggingApi)
    implementation(projects.modelCore)
    implementation(projects.native)
    implementation(projects.serviceRegistryBuilder)
    implementation(projects.stdlibJavaExtensions)

    testImplementation(testFixtures(projects.core))
    testImplementation(projects.testingBase)

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder loads services from a Gradle distribution.")
    }

    integTestImplementation(testFixtures(projects.buildProcessServices))

    integTestRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder loads services from a Gradle distribution.")
    }
    integTestDistributionRuntimeClasspath(projects.distributionsFull)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
