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
    id("gradlebuild.distribution.implementation-kotlin")
}

dependencies {
    api(projects.serviceProvider)
    api(projects.core)
    api(projects.coreApi)
    api(projects.declarativeDslApi)
    api(projects.declarativeDslCore)
    api(projects.declarativeDslEvaluator)
    api(projects.declarativeDslToolingModels)
    api(libs.kotlinStdlib)
    api(libs.inject)

    implementation(projects.concurrent)
    implementation(projects.declarativeDslInternalUtils)
    implementation(projects.baseServices)
    implementation(projects.messaging)
    implementation(projects.resources)
    implementation(projects.serviceLookup)
    implementation(projects.stdlibJavaExtensions)
    implementation(libs.guava)
    implementation(libs.kotlinReflect)

    testImplementation(libs.mockitoKotlin2)

    integTestImplementation(projects.internalTesting)
    integTestImplementation(projects.logging)
    integTestImplementation(testFixtures(projects.declarativeDslProvider))
    integTestImplementation(testFixtures(projects.toolingApi))

    testFixturesImplementation(projects.internalTesting)
    testFixturesImplementation(projects.internalIntegTesting)

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
