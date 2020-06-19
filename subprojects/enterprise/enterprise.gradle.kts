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

plugins {
    gradlebuild.distribution.`api-java`
}

dependencies {
    api(project(":baseServices")) // leaks BuildOperationNotificationListener on API

    implementation(library("jsr305"))
    implementation(library("inject"))
    implementation(project(":logging"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":launcher"))

    integTestImplementation(project(":internalTesting"))
    integTestImplementation(project(":internalIntegTesting"))

    // Dependencies of the integ test fixtures
    integTestImplementation(project(":buildOption"))
    integTestImplementation(project(":messaging"))
    integTestImplementation(project(":persistentCache"))
    integTestImplementation(project(":native"))
    integTestImplementation(library("guava"))

    integTestDistributionRuntimeOnly(project(":distributionsFull"))
}
