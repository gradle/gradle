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
    id("gradlebuild.distribution.api-java")
}

description = "Implementation of model reflection"

dependencies {
    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.logging)
    api(projects.persistentCache)
    api(projects.problemsApi)
    api(projects.serialization)
    api(projects.stdlibJavaExtensions)

    api(libs.guava)
    api(libs.jsr305)

    implementation(libs.commonsLang)
    implementation(libs.groovy)
    implementation(libs.inject)

    compileOnly(libs.errorProneAnnotations)

    testFixturesApi(testFixtures(projects.baseDiagnostics))
    testFixturesApi(testFixtures(projects.core))
    testFixturesApi(projects.internalIntegTesting)
    testFixturesImplementation(projects.baseAsm)
    testFixturesImplementation(libs.guava)
    testFixturesImplementation(libs.groovyAnt)
    testFixturesImplementation(libs.groovyDatetime)
    testFixturesImplementation(libs.groovyDateUtil)

    testImplementation(projects.processServices)
    testImplementation(projects.fileCollections)
    testImplementation(projects.native)
    testImplementation(projects.resources)
    testImplementation(testFixtures(projects.coreApi))
    testImplementation(testFixtures(projects.languageGroovy))
}
