/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty

plugins {
    `java-library`
    gradlebuild.`strict-compile`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":coreApi"))
    implementation(project(":resources"))
    implementation(project(":core"))

    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("jsch"))
    implementation(library("commons_io"))

    integTestImplementation(project(":logging"))
    integTestImplementation(testLibrary("jetty"))
    testLibraries("sshd").forEach { integTestImplementation(it) }
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":dependencyManagement")
    from(":ivy")
    from(":maven")
    from(":core")
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
