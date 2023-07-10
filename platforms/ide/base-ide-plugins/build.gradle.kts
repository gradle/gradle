/*
 * Copyright 2023 the original author or authors.
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
    id("gradlebuild.distribution.api-java")
}

description = "Basic components required by the IDE plugins project"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":core"))
    implementation(project(":core-api"))
    implementation(project(":dependency-management"))
    implementation(project(":file-collections"))
    implementation(project(":ide"))
    implementation(project(":logging"))
    implementation(project(":model-core"))
    implementation(project(":process-services"))

    implementation(libs.commonsLang)
    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.inject)

    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
}

packageCycles {
    excludePatterns.add("org/gradle/plugins/ide/idea/internal/**")
    excludePatterns.add("org/gradle/plugins/ide/idea/model/internal/**")
}
