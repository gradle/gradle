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
    api(project(":baseServices"))
    api(project(":coreApi"))
    api(project(":files"))
    api(library("guava"))
    api(library("jsr305"))
    api(library("inject"))

    implementation(project(":modelCore"))

    testImplementation(project(":internalTesting"))
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
