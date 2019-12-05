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

gradlebuildJava {
    moduleType = ModuleType.CORE
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":modelCore"))
    implementation(project(":toolingApi"))

    implementation(library("jsr305"))
    implementation(library("guava"))

    testImplementation(project(":internalTesting"))
    testImplementation(project(":modelCore"))

    integTestImplementation(project(":internalTesting"))
    integTestImplementation(project(":internalIntegTesting"))
    integTestImplementation(project(":logging")) {
        because("This isn't declared as part of integtesting's API, but should be as logging's classes are in fact visible on the API")
    }

    integTestRuntimeOnly(project(":runtimeApiInfo"))
    integTestRuntimeOnly(project(":toolingApiBuilders")) {
        because("Event handlers are in the wrong place, and should live in this project")
    }
}
