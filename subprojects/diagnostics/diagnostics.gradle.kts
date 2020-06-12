import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
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
    gradlebuild.distribution.`api-java`
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":fileCollections"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":reporting"))
    implementation(project(":platformBase"))
    implementation(project(":snapshots"))
    implementation(project(":dependencyManagement"))
    implementation(project(":baseServicesGroovy"))
    implementation(project(":buildOption"))

    implementation(library("slf4j_api"))
    implementation(library("groovy"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("inject"))
    implementation(library("jatl"))

    testImplementation(project(":processServices"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependencyManagement")))
    testImplementation(testFixtures(project(":logging")))

    integTestImplementation(testLibrary("jsoup"))
    integTestImplementation(testLibrary("jetty"))

    testFixturesApi(testFixtures(project(":platformNative")))
    testFixturesImplementation(project(":baseServices"))
    testFixturesImplementation(project(":internalIntegTesting"))
    testFixturesImplementation(library("guava"))

    testRuntimeOnly(project(":distributionsCore")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributionsFull"))  {
        because("There are integration tests that assert that all the tasks of a full distribution are reported (these should probably move to ':integTests').")
    }
}

classycle {
    excludePatterns.set(listOf(
        "org/gradle/api/reporting/model/internal/*",
        "org/gradle/api/reporting/dependencies/internal/*",
        "org/gradle/api/plugins/internal/*"))
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
