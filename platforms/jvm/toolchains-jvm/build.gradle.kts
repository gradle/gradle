/*
 * Copyright 2023 the original author or authors.
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
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.distribution.api-kotlin")
}

description = "Adds support for using JVM toolchains in projects"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":core"))
    implementation(project(":core-api"))
    implementation(project(":dependency-management"))
    implementation(project(":diagnostics"))
    implementation(project(":enterprise-operations"))
    implementation(project(":file-collections"))
    implementation(project(":jvm-services"))
    implementation(project(":logging"))
    implementation(project(":model-core"))
    implementation(project(":persistent-cache"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":resources"))

    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.nativePlatform) {
        because("Required for SystemInfo")
    }
    implementation(libs.futureKotlin("stdlib-jdk8"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":language-groovy")))
    testImplementation(testFixtures(project(":logging")))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }

    integTestImplementation(testFixtures(project(":model-core")))
    integTestImplementation(testFixtures(project(":plugins")))

    integTestImplementation(libs.slf4jApi)

    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

configurations {
    integTestImplementation {
        attributes {
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, LibraryElements.CLASSES))
        }
    }
}

packageCycles {
    // Needed for the factory methods in the interface
    excludePatterns.add("org/gradle/jvm/toolchain/JavaLanguageVersion**")
    excludePatterns.add("org/gradle/jvm/toolchain/**")
}
