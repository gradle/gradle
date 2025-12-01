/*
 * Copyright 2025 the original author or authors.
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

description = "Parameters and services for controlling the build action running against a build tree"

dependencies {
    api(projects.baseServices)
    api(projects.buildOption)
    api(projects.core)
    api(projects.loggingApi)

    api(libs.kotlinStdlib)

    implementation(projects.configurationCacheBase)

    runtimeOnly(libs.groovy)
    runtimeOnly(projects.buildEvents)
    runtimeOnly(projects.coreApi)
    runtimeOnly(projects.dependencyManagement)
    runtimeOnly(projects.encryptionServices)
    runtimeOnly(projects.flowServices)
    runtimeOnly(projects.logging)
    runtimeOnly(projects.pluginUse)

    compileOnly(libs.jspecify)
}

jvmCompile {
    compilations {
        named("main") {
            targetJvmVersion = 17
        }
    }
}
