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
import gradlebuild.cleanup.WhenNotEmpty

plugins {
    id("gradlebuild.distribution.api-kotlin")
    id("gradlebuild.kotlin-dsl-dependencies-embedded")
}

description = "Kotlin DSL Provider"

dependencies {
    api(project(":kotlinDslToolingModels"))
    api(project(":kotlinCompilerEmbeddable"))
    api(libs.futureKotlin("stdlib-jdk8"))

    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":persistentCache"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":baseServicesGroovy")) // for 'Specs'
    implementation(project(":fileCollections"))
    implementation(project(":files"))
    implementation(project(":resources"))
    implementation(project(":buildCache"))
    implementation(project(":toolingApi"))
    implementation(project(":execution"))

    implementation(libs.groovy)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.inject)

    implementation(libs.futureKotlin("scripting-common")) {
        isTransitive = false
    }
    implementation(libs.futureKotlin("scripting-jvm")) {
        isTransitive = false
    }
    implementation(libs.futureKotlin("scripting-jvm-host-embeddable")) {
        isTransitive = false
    }
    implementation(libs.futureKotlin("scripting-compiler-embeddable")) {
        isTransitive = false
    }
    implementation(libs.futureKotlin("scripting-compiler-impl-embeddable")) {
        isTransitive = false
    }
    implementation(libs.futureKotlin("sam-with-receiver-compiler-plugin")) {
        isTransitive = false
    }
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.1.0") {
        isTransitive = false
    }

    // TODO (donat) delete kotlinDslTestFixture project
    testImplementation(project(":buildCacheHttp"))
    testImplementation(project(":buildInit"))
    testImplementation(project(":jacoco"))
    testImplementation(project(":platformNative")) {
        because("BuildType from platform-native is used in ProjectAccessorsClassPathTest")
    }
    testImplementation(project(":plugins"))
    testImplementation(project(":versionControl"))
    testImplementation(libs.ant)
    testImplementation(libs.asm)
    testImplementation(libs.mockitoKotlin)
    testImplementation(libs.jacksonKotlin)

    testImplementation(libs.archunit)
    testImplementation(libs.kotlinCoroutines)
    testImplementation(libs.awaitility)

    integTestImplementation(project(":languageGroovy"))
    integTestImplementation(project(":languageGroovy")) {
        because("ClassBytesRepositoryTest makes use of Groovydoc task.")
    }
    integTestImplementation(project(":internalTesting"))
    integTestImplementation(libs.mockitoKotlin)

    testRuntimeOnly(project(":distributionsJvm")) {
        because("SimplifiedKotlinScriptEvaluator reads default imports from the distribution (default-imports.txt).")
    }

    testFixturesImplementation(project(":baseServices"))
    testFixturesImplementation(project(":coreApi"))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":resources"))
    testFixturesImplementation(project(":kotlinDslToolingBuilders"))
    testFixturesImplementation(project(":testKit"))
    testFixturesImplementation(project(":internalTesting"))
    testFixturesImplementation(project(":internalIntegTesting"))

    testFixturesImplementation(libs.junit)
    testFixturesImplementation(libs.mockitoKotlin)
    testFixturesImplementation(libs.jacksonKotlin)
    testFixturesImplementation(libs.asm)

    integTestDistributionRuntimeOnly(project(":distributionsBasics"))
}

classycle {
    excludePatterns.set(listOf("org/gradle/kotlin/dsl/**"))
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
