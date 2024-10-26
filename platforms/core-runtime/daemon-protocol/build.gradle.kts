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

description = "The messages and types sent between client and daemon"

dependencies {
    api(libs.jsr305)
    api(projects.baseServices)
    api(projects.loggingApi)
    api(projects.serialization)
    api(projects.logging)
    api(projects.stdlibJavaExtensions)
    api(projects.jvmServices)
    api(projects.messaging)
    api(projects.native)
    api(projects.toolchainsJvmShared)
    api(projects.files)
    api(projects.persistentCache)
    api(projects.serviceProvider)

    // The client should not depend on core or core-api, but core still contains some types that are shared between the client and daemon
    api(projects.coreApi)
    api(projects.core)

    implementation(libs.guava)
    implementation(libs.slf4jApi)
    implementation(projects.io)
    implementation(projects.enterpriseLogging)
    implementation(projects.time)

    testImplementation(testFixtures(projects.serialization))
    testImplementation(testFixtures(projects.time))
    testImplementation(testFixtures(projects.core))
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
