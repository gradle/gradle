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

import org.gradle.build.ClasspathManifest
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
}

dependencies {
    api(project(":core"))

    implementation(project(":resources"))
    implementation(project(":resourcesHttp"))
    implementation(project(":snapshots"))
    implementation(project(":execution"))

    implementation(library("asm"))
    implementation(library("asm_commons"))
    implementation(library("asm_util"))
    implementation(library("commons_lang"))
    implementation(library("commons_io"))
    implementation(library("ivy"))
    implementation(library("slf4j_api"))
    implementation(library("gson"))
    implementation(library("jcip"))
    implementation(library("maven3"))

    runtimeOnly(library("bouncycastle_provider"))
    runtimeOnly(project(":installationBeacon"))
    runtimeOnly(project(":compositeBuilds"))
    runtimeOnly(project(":versionControl"))

    testImplementation(library("nekohtml"))

    integTestRuntimeOnly(project(":ivy"))
    integTestRuntimeOnly(project(":maven"))
    integTestRuntimeOnly(project(":resourcesS3"))
    integTestRuntimeOnly(project(":resourcesSftp"))
    integTestRuntimeOnly(project(":testKit"))

    testFixturesApi(project(":resourcesHttp", "testFixturesApiElements"))
    testFixturesImplementation(project(":internalIntegTesting"))
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
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}

tasks.classpathManifest {
    additionalProjects = listOf(project(":runtimeApiInfo"))
}
