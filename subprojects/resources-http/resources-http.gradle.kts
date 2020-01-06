import org.gradle.gradlebuild.unittestandcompile.ModuleType

/*
 * Copyright 2014 the original author or authors.
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
    // Cannot use strict compile because JDK 7 doesn't recognize
    // @SuppressWarnings("deprecation"), used in org.gradle.internal.resource.transport.http.HttpClientHelper.AutoClosedHttpResponse
    // in the context of a delegation pattern
    // gradlebuild.`strict-compile`
    gradlebuild.classycle
}

dependencies {
    api(project(":resources"))
    implementation(project(":baseServices"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":logging"))

    implementation(library("commons_httpclient"))
    implementation(library("slf4j_api"))
    implementation(library("jcl_to_slf4j"))
    implementation(library("jcifs"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("commons_io"))
    implementation(library("xerces"))
    implementation(library("nekohtml"))

    testImplementation(project(":internalIntegTesting"))
    testImplementation(testLibrary("jetty"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":logging")))

    testFixturesImplementation(project(":baseServices"))
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(project(":internalTesting"))
    testFixturesImplementation(project(":internalIntegTesting"))
    testFixturesImplementation(library("slf4j_api"))
    integTestRuntimeOnly(project(":runtimeApiInfo"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

