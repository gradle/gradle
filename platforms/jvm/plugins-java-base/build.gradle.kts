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
    id("gradlebuild.instrumented-java-project")
}

description = "Contains a basic JVM plugin used to compile, test, and assemble Java source; often applied by other JVM plugins (though named java-base, jvm-base would be a more proper name)."

errorprone {
    disabledChecks.addAll(
        "UnusedMethod", // 1 occurrences
    )
}

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.serviceProvider)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagement)
    api(projects.languageJava)
    api(projects.languageJvm)
    api(projects.modelCore)
    api(projects.platformJvm)
    api(projects.toolchainsJvmShared)

    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.fileCollections)
    implementation(projects.logging)
    implementation(projects.platformBase)
    implementation(projects.reporting)
    implementation(projects.testingBase)
    implementation(projects.testingJvm)
    implementation(projects.toolchainsJvm)
    implementation(projects.serviceLookup)

    implementation(libs.commonsLang)
    implementation(libs.guava)

    runtimeOnly(projects.diagnostics)

    testImplementation(testFixtures(projects.core))

    integTestDistributionRuntimeOnly(projects.distributionsJvm)

    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(projects.logging)
}

packageCycles {
    excludePatterns.add("org/gradle/api/plugins/**")
}

integTest.usesJavadocCodeSnippets.set(true)
