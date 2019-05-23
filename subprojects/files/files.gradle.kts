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
    api(library("jsr305"))

    implementation(project(":baseServices"))
    implementation(project(":baseServicesGroovy"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":logging"))
    implementation(project(":native"))

    implementation(library("slf4j_api"))
    implementation(library("groovy"))
    implementation(library("guava"))
    implementation(library("commons_io"))
    implementation(library("commons_lang"))
    implementation(library("inject"))

    testImplementation(project(":processServices"))
    testImplementation(project(":resources"))
    
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
