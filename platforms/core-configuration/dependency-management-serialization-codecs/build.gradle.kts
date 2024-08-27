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
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
    id("gradlebuild.kotlin-experimental-contracts")
}

description = "Configuration Cache serialization codecs for :dependency-management types"

dependencies {
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagement)
    api(projects.enterpriseOperations)
    api(projects.execution)
    api(projects.fileCollections)
    api(projects.functional)
    api(projects.graphSerialization)
    api(projects.hashing)
    api(projects.logging)
    api(projects.modelCore)
    api(projects.problemsApi)
    api(projects.snapshots)

    api(libs.kotlinStdlib)

    implementation(projects.configurationCacheBase)
    implementation(projects.serviceLookup)
    implementation(projects.serialization)
    implementation(projects.stdlibJavaExtensions)
    implementation(projects.stdlibKotlinExtensions)

    implementation(libs.guava)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
