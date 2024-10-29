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

description = "Efficient and extensible serialization for object graphs, with the capability to isolate and manage portions of the graph independently"

dependencies {
    api(projects.configurationProblemsBase)
    api(projects.stdlibJavaExtensions)
    api(projects.loggingApi)
    api(projects.serialization)

    api(libs.guava)
    api(libs.jsr305)
    api(libs.kotlinStdlib)

    implementation(projects.baseServices)
    implementation(projects.problemsApi)
    implementation(projects.stdlibKotlinExtensions)

    implementation(libs.fastutil)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
