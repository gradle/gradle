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
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
}

description = "Shared classes for projects requiring GPG support"

dependencies {
    api(project(":coreApi"))
    api(project(":resources"))
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":resourcesHttp"))
    implementation(library("guava"))

    api(library("bouncycastle_pgp"))

    implementation(library("groovy")) {
        because("Project.exec() depends on Groovy")
    }

    testImplementation(testFixtures(project(":core")))
    testRuntimeOnly(project(":dependencyManagement"))
    testRuntimeOnly(project(":workers"))
    testRuntimeOnly(project(":runtimeApiInfo"))

    testFixturesImplementation(project(":baseServices"))
    testFixturesImplementation(library("slf4j_api"))
    testFixturesImplementation(testLibrary("jetty"))
    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation(project(":internalIntegTesting"))

}

gradlebuildJava {
    moduleType = ModuleType.CORE
}
