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

description = "Contains the JVM Test Suite plugin"

errorprone {
    disabledChecks.addAll(
        "OverridesJavaxInjectableMethod", // 1 occurrences
    )
}

dependencies {
    api(projects.javaLanguageExtensions)
    api(project(":base-services"))
    api(project(":core-api"))
    api(project(":language-jvm"))
    api(project(":model-core"))
    api(project(":platform-jvm"))
    api(project(":testing-jvm"))
    api(project(":test-suites-base"))

    api(libs.inject)

    implementation(project(":core"))
    implementation(project(":logging"))
    implementation(project(":plugins-java-base"))
    implementation(project(":testing-base"))

    implementation(libs.commonsLang)

    implementation(libs.ant)
    implementation(libs.guava)

    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}
