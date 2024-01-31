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

description = "Declarations to define JVM toolchains shared between launcher and daemon"

errorprone {
    disabledChecks.addAll(
        "StringCaseLocaleUsage", // 2 occurrences
        "UnnecessaryLambda", // 2 occurrences
    )
}

dependencies {
    implementation(project(":core"))

    implementation(libs.commonsIo)
    implementation(libs.commonsLang)

    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributions-jvm")) {
        because("ProjectBuilder tests load services from a Gradle distribution.  Toolchain usage requires JVM distribution.")
    }
}

packageCycles {
    // Needed for the factory methods in the interface
    excludePatterns.add("org/gradle/jvm/toolchain/JavaLanguageVersion**")
    excludePatterns.add("org/gradle/jvm/toolchain/**")
}

integTest.usesJavadocCodeSnippets.set(true)
