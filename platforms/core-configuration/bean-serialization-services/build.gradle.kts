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

description = "Configuration Cache services supporting bean serialization"

gradlebuildJava {
    usesJdkInternals = true
}

dependencies {
    api(projects.graphSerialization)
    api(projects.stdlibJavaExtensions)
    api(projects.modelCore)
    api(projects.persistentCache)
    api(projects.serviceProvider)

    api(libs.kotlinStdlib)

    implementation(projects.baseServices)
    implementation(projects.configurationProblemsBase)
    implementation(projects.core)
    implementation(projects.coreApi)
    implementation(projects.coreKotlinExtensions)
    implementation(projects.serviceLookup)
    implementation(projects.stdlibKotlinExtensions)

    implementation(libs.groovy)
    implementation(libs.guava)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
