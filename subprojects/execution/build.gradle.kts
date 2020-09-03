/*
 * Copyright 2018 the original author or authors.
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

description = "Execution engine that takes a unit of work and makes it happen"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":core-api"))
    implementation(project(":files"))
    implementation(project(":snapshots"))
    implementation(project(":model-core"))
    implementation(project(":persistent-cache"))
    implementation(project(":build-cache"))
    implementation(project(":build-cache-packaging"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.inject)

    testImplementation(project(":native"))
    testImplementation(project(":logging"))
    testImplementation(project(":process-services"))
    testImplementation(project(":model-core"))
    testImplementation(project(":base-services-groovy"))
    testImplementation(project(":resources"))
    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":file-collections")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":snapshots")))
    testImplementation(testFixtures(project(":core")))

    testFixturesImplementation(libs.guava)
    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":build-cache"))
    testFixturesImplementation(project(":snapshots"))
    testFixturesImplementation(project(":model-core"))

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
