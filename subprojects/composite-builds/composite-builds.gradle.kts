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
    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":dependencyManagement"))
    implementation(project(":launcher"))
    implementation(project(":pluginUse"))

    implementation(library("slf4j_api"))
    implementation(library("guava"))

    integTestImplementation(project(":buildOption"))

    integTestRuntimeOnly(project(":toolingApiBuilders"))
    integTestRuntimeOnly(project(":ide"))
    integTestRuntimeOnly(project(":pluginDevelopment"))
    integTestRuntimeOnly(project(":testKit"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":dependencyManagement")
    from(":launcher")
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
