/*
 * Copyright 2014 the original author or authors.
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

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":file-collections"))
    implementation(project(":dependency-management"))
    implementation(project(":ide"))
    implementation(project(":platform-base"))
    implementation(project(":platform-native"))
    implementation(project(":language-native"))
    implementation(project(":testing-base"))
    implementation(project(":testing-native"))

    implementation(libs.groovy)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)
    implementation(libs.plist)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":platform-native")))
    testImplementation(testFixtures(project(":language-native")))
    testImplementation(testFixtures(project(":version-control")))

    integTestImplementation(project(":native"))
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.jgit)

    testFixturesApi(testFixtures(project(":ide")))
    testFixturesImplementation(libs.plist)
    testFixturesImplementation(libs.guava)
    testFixturesImplementation(testFixtures(project(":ide")))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-native"))
}

integTest.usesSamples.set(true)
