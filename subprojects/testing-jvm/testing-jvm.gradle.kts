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
import org.gradle.gradlebuild.test.integrationtests.integrationTestUsesSampleDir

plugins {
    gradlebuild.distribution.`api-java`
}

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":fileCollections"))
    implementation(project(":jvmServices"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":dependencyManagement"))
    implementation(project(":reporting"))
    implementation(project(":diagnostics"))
    implementation(project(":platformBase"))
    implementation(project(":platformJvm"))
    implementation(project(":languageJava"))
    implementation(project(":testingBase"))

    implementation(library("slf4j_api"))
    implementation(library("groovy"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("commons_io"))
    implementation(library("asm"))
    implementation(library("junit"))
    implementation(library("testng"))
    implementation(library("inject"))
    implementation(library("bsh"))

    testImplementation(project(":baseServicesGroovy"))
    testImplementation("com.google.inject:guice:2.0") {
        because("This is for TestNG")
    }
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":testingBase")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":baseServices")))
    testImplementation(testFixtures(project(":platformNative")))

    testRuntimeOnly(project(":distributionsCore")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributionsJvm"))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API (org.gradle.api.tasks.testing.Test)
    ignoreDeprecations() // uses deprecated software model types
}

classycle {
    excludePatterns.set(listOf("org/gradle/api/internal/tasks/testing/**"))
}

tasks.named<Test>("test").configure {
    exclude("org/gradle/api/internal/tasks/testing/junit/ATestClass*.*")
    exclude("org/gradle/api/internal/tasks/testing/junit/ABroken*TestClass*.*")
    exclude("org/gradle/api/internal/tasks/testing/junit/ATestSetUpWithBrokenSetUp*.*")
    exclude("org/gradle/api/internal/tasks/testing/testng/ATestNGFactoryClass*.*")
}

integrationTestUsesSampleDir("subprojects/testing-jvm/src/main")
