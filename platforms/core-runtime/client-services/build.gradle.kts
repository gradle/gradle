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

description = "Services used by the Gradle client to interact with the daemon"

dependencies {
    api(project(":concurrent"))
    api(project(":messaging"))
    api(project(":logging"))
    api(project(":daemon-protocol"))
    api(project(":base-services"))

    // The client should not depend on core, but core still contains some types that are shared between the client and daemon
    api(project(":core"))

    implementation(libs.jsr305)
    implementation(libs.guava)
    implementation(libs.asm)
    implementation(libs.slf4jApi)
    implementation(project(":java-language-extensions"))

    testImplementation(testFixtures(project(":core"))) {
        because("ConcurrentSpecification")
    }
    testImplementation(project(":tooling-api")) {
        because("Unit tests verify serialization works with TAPI types")
    }
    testImplementation(testFixtures(project(":daemon-protocol")))
}
