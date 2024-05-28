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

description = "Types for build process and session state management"

dependencies {
    api(projects.serviceProvider)
    api(project(":core"))
    api(project(":base-services"))
    api(project(":java-language-extensions"))
    api(project(":daemon-protocol"))
    api(project(":logging"))

    implementation(project(":core-api"))
    implementation(project(":messaging"))
    implementation(project(":concurrent"))
    implementation(project(":logging-api"))
    implementation(project(":problems-api"))
}
