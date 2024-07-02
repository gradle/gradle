plugins {
    id("gradlebuild.distribution.api-kotlin")
    id("gradlebuild.kotlin-dsl-dependencies-embedded")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")
}

description = "Kotlin DSL Provider"

dependencies {

    api(projects.kotlinDslToolingModels)
    api(libs.kotlinStdlib)
    api(libs.kotlinReflect)

    implementation(projects.io)
    implementation(projects.baseServices)
    implementation(projects.enterpriseOperations)
    implementation(projects.functional)
    implementation(projects.messaging)
    implementation(projects.logging)
    implementation(projects.processServices)
    implementation(projects.persistentCache)
    implementation(projects.coreApi)
    implementation(projects.modelCore)
    implementation(projects.core)
    implementation(projects.fileCollections)
    implementation(projects.fileTemp)
    implementation(projects.files)
    implementation(projects.resources)
    implementation(projects.toolingApi)
    implementation(projects.execution)
    implementation(projects.normalizationJava)

    implementation("org.gradle:kotlin-dsl-shared-runtime")

    implementation(libs.groovy)
    implementation(libs.groovyJson)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.asm)

    implementation(libs.kotlinCompilerEmbeddable)
    implementation(libs.futureKotlin("script-runtime"))

    implementation(libs.futureKotlin("scripting-common")) {
        isTransitive = false
    }
    implementation(libs.futureKotlin("scripting-jvm")) {
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
    implementation(libs.futureKotlin("assignment-compiler-plugin-embeddable")) {
        isTransitive = false
    }
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.5.0") {
        isTransitive = false
    }

    testImplementation(projects.buildCacheHttp)
    testImplementation(projects.buildCacheLocal)
    testImplementation(projects.buildInit)
    testImplementation(projects.jacoco)
    testImplementation(projects.platformNative) {
        because("BuildType from platform-native is used in ProjectAccessorsClassPathTest")
    }
    testImplementation(projects.platformJvm)
    testImplementation(projects.versionControl)
    testImplementation(testFixtures(projects.core))
    testImplementation(libs.ant)
    testImplementation(libs.mockitoKotlin)
    testImplementation(libs.jacksonKotlin)
    testImplementation(libs.archunit)
    testImplementation(libs.kotlinCoroutines)
    testImplementation(libs.awaitility)

    integTestImplementation(projects.buildOption) {
        because("KotlinSettingsScriptIntegrationTest makes uses of FeatureFlag")
    }
    integTestImplementation(projects.languageGroovy) {
        because("ClassBytesRepositoryTest makes use of Groovydoc task.")
    }
    integTestImplementation(projects.internalTesting)
    integTestImplementation(libs.mockitoKotlin)

    testRuntimeOnly(projects.distributionsNative) {
        because("SimplifiedKotlinScriptEvaluator reads default imports from the distribution (default-imports.txt) and BuildType from platform-native is used in ProjectAccessorsClassPathTest.")
    }

    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.coreApi)
    testFixturesImplementation(projects.core)
    testFixturesImplementation(projects.fileTemp)
    testFixturesImplementation(projects.resources)
    testFixturesImplementation(projects.kotlinDslToolingBuilders)
    testFixturesImplementation(projects.testKit)
    testFixturesImplementation(projects.internalTesting)
    testFixturesImplementation(projects.internalIntegTesting)

    testFixturesImplementation(testFixtures(projects.hashing))

    testFixturesImplementation(libs.kotlinCompilerEmbeddable)

    testFixturesImplementation(libs.junit)
    testFixturesImplementation(libs.mockitoKotlin)
    testFixturesImplementation(libs.jacksonKotlin)
    testFixturesImplementation(libs.asm)

    integTestDistributionRuntimeOnly(projects.distributionsBasics)
}

packageCycles {
    excludePatterns.add("org/gradle/kotlin/dsl/**")
}

testFilesCleanup.reportOnly = true

strictCompile {
    ignoreDeprecations()
}
