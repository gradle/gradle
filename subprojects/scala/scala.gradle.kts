/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":workerProcesses"))
    implementation(project(":files"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":workers"))
    implementation(project(":platformBase"))
    implementation(project(":platformJvm"))
    implementation(project(":languageJvm"))
    implementation(project(":languageJava"))
    implementation(project(":languageScala"))
    implementation(project(":plugins"))
    implementation(project(":reporting"))
    implementation(project(":dependencyManagement"))

    implementation(library("groovy"))
    implementation(library("guava"))
    implementation(library("inject"))

    testImplementation(project(":baseServicesGroovy"))
    testImplementation(library("slf4j_api"))
    testImplementation(library("commons_io"))

    integTestImplementation(project(":jvmServices"))
    integTestRuntimeOnly(project(":ide"))
    integTestRuntimeOnly(project(":maven"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
    from(":plugins")
    from(":languageJvm")
    from(":languageScala")
}

tasks.named<Test>("integTest") {
    jvmArgs("-XX:MaxPermSize=1500m") // AntInProcessScalaCompilerIntegrationTest needs lots of permgen
}
