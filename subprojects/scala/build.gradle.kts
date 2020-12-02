/*
 * Copyright 2010 the original author or authors.
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
    implementation(project(":worker-processes"))
    implementation(project(":file-collections"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":workers"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-jvm"))
    implementation(project(":language-java"))
    implementation(project(":language-scala"))
    implementation(project(":plugins"))
    implementation(project(":reporting"))
    implementation(project(":dependency-management"))
    implementation(project(":process-services"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.inject)

    testImplementation(project(":base-services-groovy"))
    testImplementation(project(":files"))
    testImplementation(project(":resources"))
    testImplementation(libs.slf4jApi)
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":plugins")))
    testImplementation(testFixtures(project(":language-jvm")))
    testImplementation(testFixtures(project(":language-java")))

    integTestImplementation(project(":jvm-services"))
    integTestImplementation(testFixtures(project(":language-scala")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

classycle {
    excludePatterns.set(listOf("org/gradle/api/internal/tasks/scala/**",
        // Unable to change package of public API
        "org/gradle/api/tasks/ScalaRuntime*"))
}

integTest.usesSamples.set(true)
