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
    // Some cycles have been inherited from the time these classes were in :core
    // gradlebuild.classycle
}

dependencies {
    api(project(":baseServices"))
    api(project(":baseServicesGroovy"))
    api(project(":coreApi"))
    api(project(":modelCore"))
    api(library("guava"))
    api(library("jsr305"))
    api(library("inject"))

    implementation(project(":logging"))
    implementation(project(":native"))
    implementation(library("commons_io"))
    implementation(library("commons_lang"))

    testImplementation(project(":internalTesting"))
    testFixturesImplementation(project(":internalTesting"))
}

java {
    gradlebuildJava {
        moduleType = ModuleType.CORE
    }
}

testFixtures {
    from(":core")
    from(":coreApi")
}
