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

description = "Contains a basic JVM plugin used to compile, test, and assemble Java source; often applied by other JVM plugins (though named java-base, jvm-base would be a more proper name)."

errorprone {
    disabledChecks.addAll(
        "UnusedMethod", // 1 occurrences
    )
}

dependencies {
    api(projects.javaLanguageExtensions)
    api(projects.serviceProvider)
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":dependency-management"))
    api(project(":language-java"))
    api(project(":language-jvm"))
    api(project(":model-core"))
    api(project(":platform-jvm"))
    api(project(":toolchains-jvm-shared"))

    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(project(":file-collections"))
    implementation(project(":logging"))
    implementation(project(":platform-base"))
    implementation(project(":reporting"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))
    implementation(project(":toolchains-jvm"))

    implementation(libs.commonsLang)
    implementation(libs.guava)

    runtimeOnly(project(":diagnostics"))

    testImplementation(testFixtures(project(":core")))

    integTestDistributionRuntimeOnly(project(":distributions-jvm"))

    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(project(":logging"))
}

packageCycles {
    excludePatterns.add("org/gradle/api/plugins/**")
}

integTest.usesJavadocCodeSnippets.set(true)
