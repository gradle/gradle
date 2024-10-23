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
    id("gradlebuild.distribution.api-java")
}

description = "Contains the Test Report Aggregation plugin"

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.coreApi)
    api(projects.platformJvm)

    api(libs.inject)

    implementation(projects.baseServices)
    implementation(projects.core)
    implementation(projects.pluginsJavaBase)
    implementation(projects.pluginsJvmTestSuite)
    implementation(projects.reporting)
    implementation(projects.testingBase)
    implementation(projects.testingJvm)
    implementation(projects.testSuitesBase)

    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
