plugins {
    id("gradlebuild.distribution.api-kotlin")
    id("gradlebuild.kotlin-dsl-dependencies-embedded")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
}

description = "Kotlin DSL Provider"

dependencies {

    api(project(":kotlin-dsl-tooling-models"))
    api(libs.futureKotlin("stdlib-jdk8"))
    api(libs.futureKotlin("reflect"))

    implementation(project(":base-services"))
    implementation(project(":enterprise-operations"))
    implementation(project(":functional"))
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
    implementation(project(":file-temp"))
    implementation(project(":files"))
    implementation(project(":resources"))
    implementation(project(":build-cache"))
    implementation(project(":tooling-api"))
    implementation(project(":execution"))
    implementation(project(":normalization-java"))
    implementation(project(":wrapper-shared"))

    implementation(libs.groovy)
    implementation(libs.groovyJson)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.asm)

    implementation(libs.futureKotlin("compiler-embeddable"))
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
    implementation(libs.futureKotlin("assignment-compiler-plugin-embeddable")) {
        isTransitive = false
    }
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.5.0") {
        isTransitive = false
    }

    testImplementation(project(":build-cache-http"))
    testImplementation(project(":build-init"))
    testImplementation(project(":jacoco"))
    testImplementation(project(":platform-native")) {
        because("BuildType from platform-native is used in ProjectAccessorsClassPathTest")
    }
    testImplementation(project(":platform-jvm"))
    testImplementation(project(":version-control"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.ant)
    testImplementation(libs.mockitoKotlin)
    testImplementation(libs.jacksonKotlin)
    testImplementation(libs.archunit)
    testImplementation(libs.kotlinCoroutines)
    testImplementation(libs.awaitility)

    integTestImplementation(project(":build-option")) {
        because("KotlinSettingsScriptIntegrationTest makes uses of FeatureFlag")
    }
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
    testFixturesImplementation(project(":file-temp"))
    testFixturesImplementation(project(":resources"))
    testFixturesImplementation(project(":kotlin-dsl-tooling-builders"))
    testFixturesImplementation(project(":test-kit"))
    testFixturesImplementation(project(":internal-testing"))
    testFixturesImplementation(project(":internal-integ-testing"))

    testFixturesImplementation(testFixtures(project(":hashing")))

    testFixturesImplementation(libs.futureKotlin("compiler-embeddable"))

    testFixturesImplementation(libs.junit)
    testFixturesImplementation(libs.mockitoKotlin)
    testFixturesImplementation(libs.jacksonKotlin)
    testFixturesImplementation(libs.asm)

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}

packageCycles {
    excludePatterns.add("org/gradle/kotlin/dsl/**")
}

testFilesCleanup.reportOnly = true

strictCompile {
    ignoreDeprecations()
}
