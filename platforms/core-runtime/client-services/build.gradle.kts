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

description = "Services used by the Gradle client to interact with the daemon"

dependencies {
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.classloaders)
    api(projects.concurrent)
    api(projects.daemonProtocol)
    api(projects.enterpriseLogging)
    api(projects.functional)
    api(projects.jvmServices)
    api(projects.logging)
    api(projects.messaging)
    api(projects.modelCore)
    api(projects.native)
    api(projects.persistentCache)
    api(projects.processServices)
    api(projects.resources)
    api(projects.resourcesHttp)
    api(projects.scopedPersistentCache)
    api(projects.serialization)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)
    api(projects.time)
    api(projects.toolchainsJvmShared)
    api(projects.toolingApi)

    // The client should not depend on core or core-api or projects that depend on these.
    // However, these project still contains some types that are shared between the client and daemon.
    api(projects.core)
    api(projects.coreApi)
    api(projects.fileCollections)
    api(projects.fileTemp)

    api(libs.jspecify)
    api(libs.nativePlatform)

    implementation(projects.baseAsm)
    implementation(projects.buildConfiguration)
    implementation(projects.buildEvents)
    implementation(projects.buildProcessServices)
    implementation(projects.fileOperations)
    implementation(projects.files)
    implementation(projects.hashing)
    implementation(projects.instrumentationAgentServices)
    implementation(projects.io)
    implementation(projects.loggingApi)
    implementation(projects.serviceRegistryBuilder)

    implementation(libs.asm)
    implementation(libs.guava)
    implementation(libs.jsr305)
    implementation(libs.slf4jApi)

    testImplementation(testFixtures(projects.core)) {
        because("ConcurrentSpecification")
    }
    testImplementation(projects.toolingApi) {
        because("Unit tests verify serialization works with TAPI types")
    }
    testImplementation(testFixtures(projects.daemonProtocol))
}
