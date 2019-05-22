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

description = "Tools to take immutable, comparable snapshots of files and other things"

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":native"))
    implementation(project(":messaging"))
    implementation(project(":coreApi"))
    implementation(project(":files"))
    implementation(project(":persistentCache"))
    implementation(project(":modelCore"))
    
    implementation(library("guava"))
    implementation(library("jsr305"))
    implementation(library("inject"))

    testImplementation(project(":processServices"))
    testImplementation(project(":resources"))
    testImplementation(library("ant"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":baseServices")
    from(":files")
    from(":messaging")
    from(":core")
    from(":coreApi")
}
