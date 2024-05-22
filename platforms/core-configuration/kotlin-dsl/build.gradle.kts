plugins {
    id("gradlebuild.distribution.api-kotlin")
    id("gradlebuild.kotlin-dsl-dependencies-embedded")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")
}

description = "Kotlin DSL Provider"

dependencies {
    api(project(":base-services"))
    api(project(":concurrent"))
    api(project(":core-api"))
    api(project(":core"))
    api(project(":hashing"))
    api(project(":java-language-extensions"))
    api(project(":kotlin-dsl-tooling-models"))
    api(project(":logging-api"))
    api(project(":process-services"))
    api(project(":tooling-api"))

    api(libs.groovy)
    api(libs.guava)
    api(libs.inject)
    api(libs.jsr305)
    api(libs.futureKotlin("script-runtime"))
    api(libs.futureKotlin("scripting-common")) {
        isTransitive = false
    }
    api(libs.futureKotlin("scripting-compiler-impl-embeddable")) {
        isTransitive = false
    }
    api(libs.slf4jApi)
    api(libs.futureKotlin("stdlib"))

    api(libs.futureKotlin("reflect")) {
        because("Needed to avoid dependency verification issues")
    }

    implementation(project(":build-operations"))
    implementation(project(":enterprise-logging"))
    implementation(project(":enterprise-operations"))
    implementation(project(":execution"))
    implementation(project(":file-collections"))
    implementation(project(":file-temp"))
    implementation(project(":files"))
    implementation(project(":functional"))
    implementation(project(":logging"))
    implementation(project(":messaging"))
    implementation(project(":model-core"))
    implementation(project(":normalization-java"))
    implementation(projects.io)
    implementation(project(":persistent-cache"))
    implementation(project(":service-provider"))
    implementation(project(":snapshots"))
    implementation(project(":resources"))

    implementation("org.gradle:kotlin-dsl-shared-runtime")

    implementation(libs.asm)
    implementation(libs.groovyJson)

    implementation(libs.futureKotlin("compiler-embeddable"))
    implementation(libs.futureKotlin("scripting-jvm")) {
        isTransitive = false
    }
    implementation(libs.futureKotlin("scripting-compiler-embeddable")) {
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

    compileOnly(project(":base-services-groovy")) {
        because("for 'Specs'")
    }

    testImplementation(project(":build-cache-http"))
    testImplementation(project(":build-cache-local"))
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

    integTestImplementation(project(":java-language-extensions"))

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

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}

packageCycles {
    excludePatterns.add("org/gradle/kotlin/dsl/**")
}

testFilesCleanup.reportOnly = true

strictCompile {
    ignoreDeprecations()
}

dependencyAnalysis {
    issues {
        onIncorrectConfiguration {
            exclude("org.jetbrains.kotlin:kotlin-reflect:1.9.23")
        }
    }
}
