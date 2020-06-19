import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
import org.gradle.gradlebuild.test.integrationtests.integrationTestUsesSampleDir

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
    gradlebuild.distribution.`api-java`
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":fileCollections"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":baseServicesGroovy"))
    implementation(project(":dependencyManagement"))
    implementation(project(":plugins"))
    implementation(project(":platformBase"))
    implementation(project(":platformJvm"))
    implementation(project(":languageJvm"))
    implementation(project(":languageJava"))
    implementation(project(":languageScala"))
    implementation(project(":scala"))
    implementation(project(":ear"))
    implementation(project(":toolingApi"))

    implementation(library("groovy"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("commons_io"))
    implementation(library("inject"))

    testFixturesApi(project(":baseServices")) {
        because("test fixtures export the Action class")
    }
    testFixturesApi(project(":logging")) {
        because("test fixtures export the ConsoleOutput class")
    }
    testFixturesImplementation(project(":internalIntegTesting"))

    testImplementation(project(":dependencyManagement"))
    testImplementation(testLibrary("xmlunit"))
    testImplementation("nl.jqno.equalsverifier:equalsverifier:2.1.6")
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependencyManagement")))

    integTestImplementation(testLibrary("jetty"))

    testRuntimeOnly(project(":distributionsCore")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributionsJvm"))
    crossVersionTestDistributionRuntimeOnly(project(":distributionsJvm"))
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

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}

integrationTestUsesSampleDir("subprojects/ide/src/main")
