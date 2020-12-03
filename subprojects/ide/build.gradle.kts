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
    implementation(project(":process-services"))
    implementation(project(":file-collections"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":base-services-groovy"))
    implementation(project(":dependency-management"))
    implementation(project(":plugins"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-jvm"))
    implementation(project(":language-java"))
    implementation(project(":language-scala"))
    implementation(project(":scala"))
    implementation(project(":ear"))
    implementation(project(":tooling-api"))

    implementation(libs.groovy)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.inject)

    testFixturesApi(project(":base-services")) {
        because("test fixtures export the Action class")
    }
    testFixturesApi(project(":logging")) {
        because("test fixtures export the ConsoleOutput class")
    }
    testFixturesImplementation(project(":internal-integ-testing"))

    testImplementation(project(":dependency-management"))
    testImplementation(libs.xmlunit)
    testImplementation(libs.equalsverifier)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependency-management")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

strictCompile {
    ignoreRawTypes()
}

classycle {
    excludePatterns.set(listOf(
        "org/gradle/plugins/ide/internal/*",
        "org/gradle/plugins/ide/eclipse/internal/*",
        "org/gradle/plugins/ide/idea/internal/*",
        "org/gradle/plugins/ide/eclipse/model/internal/*",
        "org/gradle/plugins/ide/idea/model/internal/*"))
}

integTest.usesSamples.set(true)
testFilesCleanup.reportOnly.set(true)
