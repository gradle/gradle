import org.gradle.gradlebuild.unittestandcompile.ModuleType

/*
 * Copyright 2010 the original author or authors.
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

/*
    Provides generally useful test utilities, used for unit and integration testing.
*/
plugins {
    gradlebuild.classycle
}

dependencies {
    compile(library("groovy"))

    compile(project(":baseServices"))
    compile(project(":native"))
    compile(library("slf4j_api"))
    compile(library("guava"))
    compile(library("commons_lang"))
    compile(library("commons_io"))
    compile(library("ant"))
    compile(library("asm"))
    compile(library("asm_tree"))
    compile(library("junit"))
    compile(testLibrary("hamcrest"))
    compile(testLibrary("spock"))
    runtime(testLibrary("bytebuddy"))
    compile(testLibrary("jsoup"))
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

ideConfiguration {
    makeAllSourceDirsTestSourceDirsInIdeaModule()
}
