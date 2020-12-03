/*
 * Copyright 2012 the original author or authors.
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
    implementation(project(":file-collections"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":reporting"))
    implementation(project(":platform-base"))
    implementation(project(":snapshots"))
    implementation(project(":dependency-management"))
    implementation(project(":base-services-groovy"))
    implementation(project(":build-option"))

    implementation(libs.slf4jApi)
    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)
    implementation(libs.jatl)

    testImplementation(project(":process-services"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":logging")))

    integTestImplementation(libs.jsoup)
    integTestImplementation(libs.jetty)

    testFixturesApi(testFixtures(project(":platform-native")))
    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(libs.guava)

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))  {
        because("There are integration tests that assert that all the tasks of a full distribution are reported (these should probably move to ':integTests').")
    }
}

classycle {
    excludePatterns.set(listOf(
        "org/gradle/api/reporting/model/internal/*",
        "org/gradle/api/reporting/dependencies/internal/*",
        "org/gradle/api/plugins/internal/*"))
}
