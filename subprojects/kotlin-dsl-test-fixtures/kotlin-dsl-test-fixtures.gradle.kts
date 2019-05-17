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

    compile(project(":distributionsDependencies"))

    compile(project(":kotlinDsl"))
    compile(project(":kotlinDslToolingBuilders"))

    compile(project(":testKit"))
    compile(project(":internalIntegTesting"))

    compile(library("junit"))
    compile(testLibrary("hamcrest"))

    compile("com.nhaarman:mockito-kotlin:1.6.0")
    compile("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.2")
    compile("org.ow2.asm:asm:7.1")
}
