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

import accessors.java
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
    gradlebuild.`publish-public-libraries`
    gradlebuild.`strict-compile`
}

description = "Tools to take immutable, comparable snapshots of files and other things"

dependencies {
    api(project(":files"))
    api(project(":hashing"))

    implementation(project(":baseAnnotations"))

    implementation(library("guava")) { version { require(libraryVersion("guava")) } }
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

afterEvaluate {
    // This is a workaround for the validate plugins task trying to inspect classes which have changed but are NOT tasks.
    // For the current project, we exclude all internal packages, since there are no tasks in there.
    tasks.withType<ValidatePlugins>().configureEach {
        val main by project.java.sourceSets
        classes.setFrom(main.output.classesDirs.asFileTree.matching { exclude("**/internal/**") })
    }
}
