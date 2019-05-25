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
    `kotlin-library`
}

description = "Kotlin DSL Test Fixtures"

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

dependencies {
    api(project(":kotlinDsl"))
    
    implementation(project(":baseServices"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":resources"))
    implementation(project(":kotlinDslToolingBuilders"))
    implementation(project(":testKit"))
    implementation(project(":internalTesting"))
    implementation(project(":internalIntegTesting"))

    implementation(library("junit"))
    implementation(testLibrary("mockito_kotlin"))
    implementation(testLibrary("jackson_kotlin"))
    implementation("org.ow2.asm:asm:7.1")
}
