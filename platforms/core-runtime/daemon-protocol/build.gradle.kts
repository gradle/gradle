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
    id("gradlebuild.distribution.api-java")
}

description = "The messages and types sent between client and daemon"

dependencies {
    api(libs.jsr305)
    api(project(":base-services"))
    api(project(":logging-api"))
    api(project(":serialization"))
    api(project(":logging"))
    api(project(":java-language-extensions"))

    // The client should not depend on core or core-api, but core still contains some types that are shared between the client and daemon
    api(project(":core-api"))
    api(project(":core"))

    implementation(libs.guava)
    implementation(libs.slf4jApi)
    implementation(project(":io"))
    implementation(project(":enterprise-logging"))

    testImplementation(testFixtures(project(":serialization")))
}
