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


plugins {
    gradlebuild.distribution.`implementation-kotlin`
}

description = "Kotlin DSL Tooling Builders for IDEs"

dependencies {
    implementation(project(":kotlinDsl"))

    implementation(project(":baseServices"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":resources"))
    implementation(project(":platformBase"))
    implementation(project(":platformJvm"))
    implementation(project(":plugins"))
    implementation(project(":toolingApi"))

    testImplementation(project(":kotlinDslTestFixtures"))
    integTestImplementation(project(":kotlinDslTestFixtures"))
    integTestImplementation(project(":internalTesting"))

    crossVersionTestImplementation(project(":persistentCache"))
    crossVersionTestImplementation(library("slf4j_api"))
    crossVersionTestImplementation(library("guava"))
    crossVersionTestImplementation(library("ant"))

    integTestDistributionRuntimeOnly(project(":distributionsBasics"))
    crossVersionTestDistributionRuntimeOnly(project(":distributionsBasics"))
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
