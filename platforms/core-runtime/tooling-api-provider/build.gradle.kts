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

description = "The Tooling API provider implementation (the version-specific part that is loaded into the client)"

dependencies {
    api(projects.toolingApi)

    implementation(projects.baseServices)
    implementation(projects.buildDiscoveryApi)
    implementation(projects.buildState)
    implementation(projects.classloaders)
    implementation(projects.instrumentationAgentServices)
    implementation(projects.launcher)
    implementation(projects.logging)
    implementation(projects.native)
    implementation(projects.serviceLookup)
    implementation(projects.serviceProvider)
    implementation(projects.serviceRegistryBuilder)
    implementation(projects.stdlibJavaExtensions)

    implementation(libs.jspecify)
    implementation(libs.slf4jApi)
}
