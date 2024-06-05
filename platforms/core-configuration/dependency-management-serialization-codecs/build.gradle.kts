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
    api(project(":base-services"))
    api(project(":build-operations"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":dependency-management"))
    api(project(":enterprise-operations"))
    api(project(":execution"))
    api(project(":file-collections"))
    api(project(":functional"))
    api(projects.graphSerialization)
    api(project(":hashing"))
    api(project(":logging"))
    api(project(":model-core"))
    api(project(":snapshots"))

    api(libs.kotlinStdlib)

    implementation(projects.configurationCacheBase)
    implementation(projects.serialization)
    implementation(projects.stdlibKotlinExtensions)

    implementation(libs.guava)
}
