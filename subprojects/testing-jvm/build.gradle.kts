/*
 * Copyright 2019 the original author or authors.
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

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":file-collections"))
    implementation(project(":jvm-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":dependency-management"))
    implementation(project(":reporting"))
    implementation(project(":diagnostics"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-java"))
    implementation(project(":testing-base"))

    implementation(libs.slf4jApi)
    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.asm)
    implementation(libs.junit)
    implementation(libs.testng)
    implementation(libs.inject)
    implementation(libs.bsh)

    testImplementation(project(":base-services-groovy"))
    testImplementation(libs.guice) {
        because("This is for TestNG")
    }
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":testing-base")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":platform-native")))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API (org.gradle.api.tasks.testing.Test)
    ignoreDeprecations() // uses deprecated software model types
}

classycle {
    excludePatterns.set(listOf("org/gradle/api/internal/tasks/testing/**"))
}

tasks.test {
    exclude("org/gradle/api/internal/tasks/testing/junit/AJunit*.*")
    exclude("org/gradle/api/internal/tasks/testing/junit/BJunit*.*")
    exclude("org/gradle/api/internal/tasks/testing/junit/ATestClass*.*")
    exclude("org/gradle/api/internal/tasks/testing/junit/ATestSetUp*.*")
    exclude("org/gradle/api/internal/tasks/testing/junit/ABroken*TestClass*.*")
    exclude("org/gradle/api/internal/tasks/testing/junit/ATestSetUpWithBrokenSetUp*.*")
    exclude("org/gradle/api/internal/tasks/testing/testng/ATestNGFactoryClass*.*")
}

integTest.usesSamples.set(true)
