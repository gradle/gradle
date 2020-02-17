/*
 * Copyright 2020 the original author or authors.
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

description = "API extraction for Java"

plugins {
    `java-library`
    gradlebuild.classycle
    gradlebuild.`publish-public-libraries`
    gradlebuild.`strict-compile`
}

dependencies {
    implementation(project(":baseAnnotations"))

    implementation(library("asm")) { version { require(libraryVersion("asm")) } }
    implementation(library("guava")) { version { require(libraryVersion("guava")) } }

    testImplementation(project(":baseServices"))
    testImplementation(project(":internalTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}
