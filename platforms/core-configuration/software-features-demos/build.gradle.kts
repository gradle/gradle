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
}

description = "Demos of Software Features and Types"

dependencies {
    api(projects.antlr)
    api(projects.baseServices)
    api(projects.codeQuality)
    api(projects.core)
    api(projects.coreApi)
    api(projects.declarativeDslApi)
    api(projects.reporting)
    api(projects.softwareFeatures)

    api(libs.kotlinStdlib)
    api(libs.inject)
    api(libs.jspecify)
    api(libs.jsr305)

    runtimeOnly(libs.groovy)

    implementation(projects.languageJava)
    implementation(projects.languageGroovy)
    implementation(projects.modelCore)
    implementation(projects.platformBase)
    implementation(projects.platformJvm)

    implementation(libs.commonsLang)

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}

