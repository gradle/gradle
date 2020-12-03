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

plugins {
    id("gradlebuild.distribution.api-kotlin")
    id("gradlebuild.kotlin-dsl-dependencies-embedded")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
}

description = "Kotlin DSL Provider"

dependencies {

    compileOnlyApi(libs.futureKotlin("compiler-embeddable"))
    compileOnlyApi(libs.futureKotlin("reflect"))

    runtimeOnly(project(":kotlin-compiler-embeddable"))

    api(project(":kotlin-dsl-tooling-models"))
    api(libs.futureKotlin("stdlib-jdk8"))

    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":persistent-cache"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":base-services-groovy")) // for 'Specs'
    implementation(project(":file-collections"))
    implementation(project(":files"))
    implementation(project(":resources"))
    implementation(project(":build-cache"))
    implementation(project(":tooling-api"))
    implementation(project(":execution"))
    implementation(project(":normalization-java"))

    implementation(libs.groovy)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.asm)

    implementation(libs.futureKotlin("script-runtime"))
    implementation(libs.futureKotlin("daemon-embeddable"))

    implementation(libs.futureKotlin("scripting-common")) {
        isTransitive = false
    }
    implementation(libs.futureKotlin("scripting-jvm")) {
        isTransitive = false
    }
    implementation(libs.futureKotlin("scripting-jvm-host")) {
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

    testImplementation(project(":build-cache-http"))
    testImplementation(project(":build-init"))
    testImplementation(project(":jacoco"))
    testImplementation(project(":platform-native")) {
        because("BuildType from platform-native is used in ProjectAccessorsClassPathTest")
    }
    testImplementation(project(":plugins"))
    testImplementation(project(":version-control"))
    testImplementation(libs.ant)
    testImplementation(libs.mockitoKotlin)
    testImplementation(libs.jacksonKotlin)

    testImplementation(libs.archunit)
    testImplementation(libs.kotlinCoroutines)
    testImplementation(libs.awaitility)

    integTestImplementation(project(":language-groovy"))
    integTestImplementation(project(":language-groovy")) {
        because("ClassBytesRepositoryTest makes use of Groovydoc task.")
    }
    integTestImplementation(project(":internal-testing"))
    integTestImplementation(libs.mockitoKotlin)

    testRuntimeOnly(project(":distributions-native")) {
        because("SimplifiedKotlinScriptEvaluator reads default imports from the distribution (default-imports.txt) and BuildType from platform-native is used in ProjectAccessorsClassPathTest.")
    }

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":resources"))
    testFixturesImplementation(project(":kotlin-dsl-tooling-builders"))
    testFixturesImplementation(project(":test-kit"))
    testFixturesImplementation(project(":internal-testing"))
    testFixturesImplementation(project(":internal-integ-testing"))

    testFixturesImplementation(libs.junit)
    testFixturesImplementation(libs.mockitoKotlin)
    testFixturesImplementation(libs.jacksonKotlin)
    testFixturesImplementation(libs.asm)

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}

classycle {
    excludePatterns.set(listOf("org/gradle/kotlin/dsl/**"))
}

testFilesCleanup.reportOnly.set(true)
