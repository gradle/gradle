/*
 * Copyright 2011 the original author or authors.
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
    gradlebuild.`strict-compile`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":native"))
    implementation(project(":processServices"))
    implementation(project(":files"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":workers"))
    implementation(project(":platformBase"))
    implementation(project(":diagnostics"))

    implementation(library("nativePlatform"))
    implementation(library("groovy"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("commons_io"))
    implementation(library("snakeyaml"))
    implementation(library("gson"))
    implementation(library("inject"))

    integTestRuntimeOnly(project(":maven"))
    // Required to test visual studio project file generation for generated sources
    integTestRuntimeOnly(project(":ideNative"))

    testFixturesImplementation(project(":resources"))
    testFixturesImplementation(project(":internalIntegTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
    from(":core", "testFixtures")
    from(":ide", "testFixtures")
    from(":messaging")
    from(":platformBase")
    from(":modelCore")
    from(":diagnostics")
    from(":baseServices")
}

classycle {
    excludePatterns.set(listOf(
        "org/gradle/nativeplatform/plugins/**",
        "org/gradle/nativeplatform/tasks/**",
        "org/gradle/nativeplatform/internal/resolve/**",
        "org/gradle/nativeplatform/toolchain/internal/**"
    ))
}
