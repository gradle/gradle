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

description = "File system watchers for keeping the VFS up-to-date"

dependencies {
    api(project(":snapshots"))

    implementation(project(":baseAnnotations"))

    implementation(library("guava"))
    implementation(library("nativePlatform"))
    implementation(library("slf4j_api"))

    testImplementation(project(":processServices"))
    testImplementation(project(":resources"))
    testImplementation(project(":persistentCache"))
    testImplementation(project(":buildOption"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":fileCollections")))
    testImplementation(library("commons_io"))

    integTestDistributionRuntimeOnly(project(":distributionsCore"))
}
