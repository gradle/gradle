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
    api(project(":core"))
    api(project(":dependencyManagement"))
    api(project(":plugins"))
    api(project(":pluginUse"))
    api(project(":publish"))

    implementation(library("slf4j_api"))
    implementation(library("groovy"))
    implementation(library("ivy"))
    implementation(library("maven3"))
    implementation(library("pmaven_common"))
    implementation(library("pmaven_groovy"))
    implementation(library("maven3_wagon_file"))
    implementation(library("maven3_wagon_http"))
    implementation(library("plexus_container"))
    implementation(library("aether_connector"))

    testImplementation(testLibrary("xmlunit"))

    integTestImplementation(project(":ear"))
    integTestRuntimeOnly(project(":resourcesS3"))
    integTestRuntimeOnly(project(":resourcesSftp"))

    testFixturesImplementation(project(":internalIntegTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.PLUGIN
}

testFixtures {
    from(":core")
    from(":modelCore")
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
