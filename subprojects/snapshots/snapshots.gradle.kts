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
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.`publish-public-libraries`
    gradlebuild.classycle
}

description = "Tools to take immutable, comparable snapshots of files and other things"

dependencies {
    implementation(library("guava")) { version { require(libraryVersion("guava")) } }
    implementation(library("jsr305")) { version { require(libraryVersion("jsr305")) } }
    implementation(project(":files"))
    implementation(project(":hashing"))
    implementation(project(":pineapple"))
    implementation(library("slf4j_api")) { version { require(libraryVersion("slf4j_api")) } }

    testImplementation(project(":processServices"))
    testImplementation(project(":resources"))
    testImplementation(project(":native"))
    testImplementation(project(":persistentCache"))
    testImplementation(library("ant"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":coreApi")))
    testImplementation(testFixtures(project(":baseServices")))
    testImplementation(testFixtures(project(":fileCollections")))
    testImplementation(testFixtures(project(":messaging")))

    testRuntimeOnly(project(":runtimeApiInfo"))
    testRuntimeOnly(project(":workers"))
    testRuntimeOnly(project(":dependencyManagement"))

    testFixturesImplementation(project(":baseServices"))
    testFixturesImplementation(project(":coreApi"))
    testFixturesImplementation(project(":fileCollections"))

    integTestRuntimeOnly(project(":apiMetadata"))
    integTestRuntimeOnly(project(":kotlinDsl"))
    integTestRuntimeOnly(project(":kotlinDslProviderPlugins"))
    integTestRuntimeOnly(project(":kotlinDslToolingBuilders"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

