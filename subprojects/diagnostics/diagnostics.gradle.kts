import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
import org.gradle.gradlebuild.unittestandcompile.ModuleType

/*
 * Copyright 2012 the original author or authors.
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
    `java-library`
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":files"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":reporting"))
    implementation(project(":platformBase"))
    implementation(project(":snapshots"))
    implementation(project(":dependencyManagement"))
    implementation(project(":baseServicesGroovy"))

    implementation(library("slf4j_api"))
    implementation(library("groovy"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("inject"))
    implementation(library("jatl"))

    testImplementation(project(":processServices"))

    integTestImplementation(testLibrary("jsoup"))
    integTestImplementation(testLibrary("jetty"))

    testFixturesImplementation(project(":internalIntegTesting"))

    integTestRuntimeOnly(project(":plugins"))
    integTestRuntimeOnly(project(":platformNative"))
    integTestRuntimeOnly(project(":languageNative"))
}

testFixtures {
    from(":core")
    from(":dependencyManagement")
    from(":platformNative", "testFixtures")
    from(":logging")
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
