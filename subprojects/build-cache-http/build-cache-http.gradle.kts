import org.gradle.gradlebuild.unittestandcompile.ModuleType

/*
 * Copyright 2016 the original author or authors.
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
plugins {
    `java-library`
    gradlebuild.`strict-compile`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":buildCache"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":resources"))
    implementation(project(":resourcesHttp"))

    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("commons_httpclient"))
    implementation(library("commons_lang"))
    implementation(library("inject"))

    integTestImplementation(project(":logging"))
    integTestImplementation(testLibrary("jetty"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
}
