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
}

description = "Configuration Cache serialization codecs for :core (and family) types"

gradlebuildJava {
    usesFutureStdlib = true
}

dependencies {
    api(projects.baseServices)
    api(projects.configurationCacheBase)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagement)
    api(projects.fileCollections)
    api(projects.fileOperations)
    api(projects.flowServices)
    api(projects.graphSerialization)
    api(projects.stdlibJavaExtensions)
    api(projects.logging)
    api(projects.modelCore)
    api(projects.snapshots)

    api(libs.kotlinStdlib)
    api(libs.slf4jApi)

    implementation(projects.baseServicesGroovy)
    implementation(projects.beanSerializationServices)
    implementation(projects.buildOperations)
    implementation(projects.configurationProblemsBase)
    implementation(projects.coreKotlinExtensions)
    implementation(projects.execution)
    implementation(projects.functional)
    implementation(projects.hashing)
    implementation(projects.loggingApi)
    implementation(projects.messaging)
    implementation(projects.platformJvm)
    implementation(projects.publish)
    implementation(projects.serialization)
    implementation(projects.serviceLookup)
    implementation(projects.stdlibKotlinExtensions)

    implementation(libs.asm)
    implementation(libs.commonsLang3)
    implementation(libs.fastutil)
    implementation(libs.groovy)
    implementation(libs.guava)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
