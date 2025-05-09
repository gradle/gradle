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

description = "Types for build process and session state management"

dependencies {
    api(projects.baseServices)
    api(projects.classloaders)
    api(projects.core)
    api(projects.daemonProtocol)
    api(projects.instrumentationAgentServices)
    api(projects.logging)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.serviceRegistryBuilder)
    api(projects.stdlibJavaExtensions)

    implementation(projects.buildOperationsTrace)
    implementation(projects.buildProcessServices)
    implementation(projects.buildProcessStartup)
    implementation(projects.concurrent)
    implementation(projects.coreApi)
    implementation(projects.loggingApi)
    implementation(projects.messaging)
    implementation(projects.problemsApi)
    implementation(projects.serialization)

    implementation(libs.jspecify)

    testImplementation(projects.serviceRegistryImpl)
}
