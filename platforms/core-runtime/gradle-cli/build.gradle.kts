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

description = "Implementation of the `gradle` command"

dependencies {
    api(libs.jsr305)
    api(project(":base-services"))
    api(project(":cli"))
    api(project(":client-services"))
    api(project(":concurrent"))
    api(project(":daemon-protocol"))
    api(project(":logging"))
    api(project(":logging-api"))

    // The client is able to run builds, so uses core and other projects
    api(project(":core"))
    api(project(":launcher"))

    implementation(libs.commonsLang)
    implementation(libs.guava)
    implementation(libs.commonsIo)
    implementation(libs.ant)
    implementation(libs.groovy)
    implementation(project(":build-option"))
    implementation(project(":build-state"))
    implementation(project(":core-api"))
    implementation(project(":daemon-services"))
    implementation(project(":enterprise-logging"))
    implementation(project(":file-collections"))
    implementation(project(":instrumentation-agent-services"))
    implementation(project(":jvm-services"))
    implementation(project(":native"))
    implementation(project(":service-provider"))
    implementation(project(":stdlib-java-extensions"))
    implementation(project(":toolchains-jvm-shared"))

    testImplementation(project(":kotlin-dsl"))
    testImplementation(testFixtures(project(":logging")))
    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
}
