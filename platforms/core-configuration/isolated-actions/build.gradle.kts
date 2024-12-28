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
}

description = "Isolates actions via serialization"

dependencies {
    api(projects.baseServices)
    api(projects.configurationProblemsBase)
    api(projects.coreSerializationCodecs)
    api(projects.fileCollections)
    api(projects.graphSerialization)
    api(projects.modelCore)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)

    implementation(projects.configurationCacheBase)
    implementation(projects.core)
    implementation(projects.coreApi)
    implementation(projects.guavaSerializationCodecs)
    implementation(projects.logging)
    implementation(projects.loggingApi)
    implementation(projects.serialization)
    implementation(projects.stdlibKotlinExtensions)
    implementation(projects.stdlibSerializationCodecs)

    api(libs.kotlinStdlib)

    implementation(libs.slf4jApi)

    testImplementation(libs.mockitoKotlin2)
    testImplementation(projects.beanSerializationServices)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.logging))
    testImplementation(testFixtures(projects.persistentCache))

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}
