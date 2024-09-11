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
    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.ide)

    implementation(projects.stdlibJavaExtensions)
    implementation(projects.core)
    implementation(projects.logging)
    implementation(projects.loggingApi)
    implementation(projects.processServices)

    implementation(libs.commonsLang)
    implementation(libs.guava)

    runtimeOnly(projects.dependencyManagement)
    runtimeOnly(libs.groovy)

    testImplementation(testFixtures(projects.core))

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
}

packageCycles {
    excludePatterns.add("org/gradle/plugins/ide/idea/internal/**")
    excludePatterns.add("org/gradle/plugins/ide/idea/model/internal/**")
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
