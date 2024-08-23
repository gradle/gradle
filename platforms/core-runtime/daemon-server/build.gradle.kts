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
    id("gradlebuild.distribution.implementation-java")
}

description = "Implementation of the Gradle daemon server"

dependencies {
    api(projects.launcher)
    api(projects.logging)
    api(projects.messaging)

    implementation(libs.guava)
    implementation(projects.baseServices)
    implementation(projects.concurrent)
    implementation(projects.instrumentationAgentServices)
    implementation(projects.stdlibJavaExtensions)
    implementation(projects.loggingApi)
    implementation(projects.native)
    implementation(projects.serialization)
    implementation(projects.serviceLookup)
    implementation(projects.core)
    implementation(projects.daemonProtocol)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
