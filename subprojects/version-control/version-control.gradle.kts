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
    gradlebuild.`strict-compile`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":files"))
    implementation(project(":persistentCache"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":resources"))
    implementation(project(":dependencyManagement"))

    implementation(library("guava"))
    implementation(library("inject"))
    implementation(library("jgit"))
    implementation(library("commons_httpclient"))
    implementation(library("jsch"))

    testImplementation(project(":native"))
    testImplementation(project(":processServices"))
    
    testFixturesImplementation(project(":internalTesting"))
    testFixturesImplementation(project(":internalIntegTesting"))

    testFixturesImplementation(library("jgit"))
    testFixturesImplementation(library("commons_io"))
    testFixturesImplementation(library("commons_httpclient"))
    testFixturesImplementation(library("jsch"))
    
    integTestImplementation(project(":launcherStartup"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
}
