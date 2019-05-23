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
    gradlebuild.`strict-compile`
    gradlebuild.classycle
}

description = "Execution engine that takes a unit of work and makes it happen"

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":coreApi"))
    implementation(project(":snapshots"))
    implementation(project(":persistentCache"))
    implementation(project(":buildCache"))
    implementation(project(":buildCachePackaging"))

    implementation(library("jsr305"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("commons_io"))
    implementation(library("commons_lang"))
    implementation(library("inject"))

    testImplementation(project(":native"))
    testImplementation(project(":logging"))
    testImplementation(project(":processServices"))
    testImplementation(project(":modelCore"))
    testImplementation(project(":baseServicesGroovy"))
    testImplementation(project(":resources"))
}

java {
    gradlebuildJava {
        moduleType = ModuleType.CORE
    }
}

testFixtures {
    from(":baseServices")
    from(":files")
    from(":messaging")
    from(":snapshots")
    from(":core")
}
