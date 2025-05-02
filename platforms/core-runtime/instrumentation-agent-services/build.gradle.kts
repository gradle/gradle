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

description = "Controls for the instrumentation agent potentially applied to the process"

dependencies {
    api(projects.stdlibJavaExtensions)

    implementation(projects.classloaders)
    implementation(projects.functional)

    implementation(libs.jspecify)
    implementation(libs.slf4jApi)

    integTestImplementation(projects.launcher)
    integTestDistributionRuntimeOnly(projects.distributionsCore)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
