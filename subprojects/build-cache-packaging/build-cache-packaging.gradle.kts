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
    gradlebuild.classycle
}

description = "Package build cache results"

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":native"))
    implementation(project(":coreApi"))
    implementation(project(":buildCache"))
    implementation(project(":snapshots"))

    implementation(library("jsr305"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("inject"))
    implementation(library("commons_compress"))
    implementation(library("commons_io"))

    testImplementation(project(":processServices"))
    testImplementation(project(":files"))
    testImplementation(project(":resources"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":baseServices")
    from(":snapshots")
    from(":core")
    from(":coreApi")
}
