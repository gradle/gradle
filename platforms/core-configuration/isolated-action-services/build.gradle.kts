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

description = "Supporting services for the isolation of IsolatedAction instances"

dependencies {
    implementation(projects.baseServices)
    implementation(projects.configurationCacheBase)
    implementation(projects.configurationProblemsBase)
    implementation(projects.core)
    implementation(projects.coreApi)
    implementation(projects.coreSerializationCodecs)
    implementation(projects.fileCollections)
    implementation(projects.graphSerialization)
    implementation(projects.graphIsolation)
    implementation(projects.modelCore)
    implementation(projects.serviceProvider)
    implementation(projects.stdlibJavaExtensions)
    implementation(projects.stdlibKotlinExtensions)
    implementation(projects.stdlibSerializationCodecs)

    implementation(libs.jspecify)
    implementation(libs.kotlinStdlib)
    implementation(libs.slf4jApi)

    testImplementation(projects.beanSerializationServices)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.beanSerializationServices))
    testImplementation(libs.mockitoKotlin)
}
