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

/*
 * This project can NOT be named plugins-java-test-fixtures, as that would introduce a capabilities conflict
 * with the test fixtures of the plugins-java project.
 *
 * As this is used to create test fixtures using multiple JVM languages, this is a more appropriate name for
 * the project anyways, but it regrettably does not match the name of the plugin.
 */
description = "Contains the Java Test Fixtures plugin"

dependencies {
    api(projects.coreApi)

    api(libs.inject)

    implementation(projects.baseServices)
    implementation(projects.dependencyManagement)
    implementation(projects.core)
    implementation(projects.languageJava)
    implementation(projects.platformJvm)
    implementation(projects.pluginsJava)
    implementation(projects.pluginsJavaBase)
    implementation(projects.pluginsJvmTestSuite)

    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(projects.logging)

    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
