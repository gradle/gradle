/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
}

dependencies {
    api(library("jsr305"))

    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":files"))
    implementation(project(":persistentCache"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":baseServicesGroovy"))
    implementation(project(":buildCache"))
    implementation(project(":core"))
    implementation(project(":resources"))
    implementation(project(":resourcesHttp"))
    implementation(project(":snapshots"))
    implementation(project(":execution"))

    implementation(library("slf4j_api"))
    implementation(library("groovy"))
    implementation(library("asm"))
    implementation(library("asm_commons"))
    implementation(library("asm_util"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("commons_io"))
    implementation(library("commons_httpclient"))
    implementation(library("inject"))
    implementation(library("gson"))
    implementation(library("ant"))
    implementation(library("ivy"))
    implementation(library("maven3"))

    runtimeOnly(library("bouncycastle_provider"))
    runtimeOnly(project(":installationBeacon"))
    runtimeOnly(project(":compositeBuilds"))
    runtimeOnly(project(":versionControl"))

    testImplementation(project(":processServices"))
    testImplementation(project(":diagnostics"))
    testImplementation(project(":buildCachePackaging"))
    testImplementation(library("nekohtml"))

    integTestImplementation(project(":buildOption"))
    integTestImplementation(library("jansi"))
    integTestImplementation(library("ansi_control_sequence_util"))

    integTestRuntimeOnly(project(":ivy"))
    integTestRuntimeOnly(project(":maven"))
    integTestRuntimeOnly(project(":resourcesS3"))
    integTestRuntimeOnly(project(":resourcesSftp"))
    integTestRuntimeOnly(project(":testKit"))

    testFixturesImplementation(project(":core", "testFixturesApiElements"))
    testFixturesImplementation(project(":resourcesHttp", "testFixturesApiElements"))
    testFixturesImplementation(project(":internalTesting"))
    testFixturesImplementation(project(":internalIntegTesting"))
    testFixturesImplementation(testLibrary("jetty"))

    crossVersionTestRuntimeOnly(project(":maven"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
    from(":messaging")
    from(":coreApi")
    from(":versionControl")
    from(":resourcesHttp")
    from(":baseServices")
    from(":snapshots")
    from(":execution")
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}

tasks.classpathManifest {
    additionalProjects = listOf(project(":runtimeApiInfo"))
}
