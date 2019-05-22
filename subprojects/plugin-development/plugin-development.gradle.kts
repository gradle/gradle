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
    gradlebuild.`strict-compile`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":dependencyManagement"))
    implementation(project(":maven"))
    implementation(project(":ivy"))
    implementation(project(":platformJvm"))
    implementation(project(":reporting"))
    implementation(project(":testingBase"))
    implementation(project(":testingJvm"))
    implementation(project(":plugins"))
    implementation(project(":pluginUse"))
    implementation(project(":publish"))

    implementation(library("slf4j_api"))
    implementation(library("groovy"))
    implementation(library("commons_io"))
    implementation(library("guava"))
    implementation(library("inject"))
    implementation(library("asm"))

    testImplementation(project(":files"))

    testRuntimeOnly(project(":toolingApi"))
    testRuntimeOnly(project(":launcher"))
    testRuntimeOnly(project(":testKit"))

    integTestImplementation(project(":baseServicesGroovy"))
    integTestImplementation(library("jetbrains_annotations"))
    
    integTestRuntimeOnly(project(":toolingApiBuilders"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
    from(":logging")
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
