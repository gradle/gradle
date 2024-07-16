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
    api(projects.baseServices)
    api(projects.cli)
    api(projects.clientServices)
    api(projects.concurrent)
    api(projects.daemonProtocol)
    api(projects.logging)
    api(projects.loggingApi)
    api(projects.serviceLookup)

    // The client is able to run builds, so uses core and other projects
    api(projects.core)
    api(projects.launcher)

    implementation(libs.commonsLang)
    implementation(libs.guava)
    implementation(libs.commonsIo)
    implementation(libs.ant)
    implementation(libs.groovy)
    implementation(projects.buildOption)
    implementation(projects.buildState)
    implementation(projects.coreApi)
    implementation(projects.daemonServices)
    implementation(projects.enterpriseLogging)
    implementation(projects.fileCollections)
    implementation(projects.instrumentationAgentServices)
    implementation(projects.jvmServices)
    implementation(projects.native)
    implementation(projects.serviceProvider)
    implementation(projects.serviceRegistryBuilder)
    implementation(projects.stdlibJavaExtensions)

    testImplementation(projects.kotlinDsl)
    testImplementation(testFixtures(projects.logging))
    testRuntimeOnly(projects.distributionsCore) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
