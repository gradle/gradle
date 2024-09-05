plugins {
    id("gradlebuild.distribution.api-kotlin")
    id("gradlebuild.kotlin-dsl-dependencies-embedded")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")
}

description = "Kotlin DSL Provider"

dependencies {
    api(projects.buildProcessServices)
    api(projects.baseServices)
    api(projects.processServices)
    api(projects.classloading)
    api(projects.core)
    api(projects.coreApi)
    api(projects.concurrent)
    api(projects.fileOperations)
    api(projects.hashing)
    api(projects.kotlinDslToolingModels)
    api(projects.loggingApi)
    api(projects.stdlibJavaExtensions)
    api(projects.toolingApi)

    api(libs.groovy)
    api(libs.guava)
    api(libs.kotlinStdlib)
    api(libs.jsr305)
    api(libs.inject)
    api(libs.slf4jApi)

    implementation(projects.baseAsm)
    implementation(projects.instrumentationReporting)
    implementation(projects.buildOperations)
    implementation(projects.buildOption)
    implementation(projects.enterpriseLogging)
    implementation(projects.enterpriseOperations)
    implementation(projects.execution)
    implementation(projects.fileCollections)
    implementation(projects.fileTemp)
    implementation(projects.files)
    implementation(projects.functional)
    implementation(projects.io)
    implementation(projects.logging)
    implementation(projects.messaging)
    implementation(projects.modelCore)
    implementation(projects.normalizationJava)
    implementation(projects.persistentCache)
    implementation(projects.resources)
    implementation(projects.serviceLookup)
    implementation(projects.serviceProvider)
    implementation(projects.snapshots)

    implementation("org.gradle:java-api-extractor")
    implementation("org.gradle:kotlin-dsl-shared-runtime")

    implementation(libs.asm)
    implementation(libs.groovyJson)
    implementation(libs.kotlinReflect)

    implementation(libs.kotlinCompilerEmbeddable)
    api(libs.futureKotlin("script-runtime"))

    api(libs.futureKotlin("scripting-common")) {
        isTransitive = false
    }
    implementation(libs.futureKotlin("scripting-jvm")) {
        isTransitive = false
    }
    implementation(libs.futureKotlin("scripting-compiler-embeddable")) {
        isTransitive = false
    }
    api(libs.futureKotlin("scripting-compiler-impl-embeddable")) {
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
tasks.isolatedProjectsIntegTest {
    enabled = false
}
