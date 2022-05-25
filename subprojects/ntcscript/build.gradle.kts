plugins {
    id("gradlebuild.distribution.api-kotlin")
}

description = "Non-Turing Complete DSL Provider"

dependencies {

    implementation(kotlin("stdlib-jdk8"))
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

    implementation(libs.tomlj)
    implementation(libs.groovy)
    implementation(libs.groovyJson)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.asm)

    testImplementation(project(":build-cache-http"))
    testImplementation(project(":build-init"))
    testImplementation(project(":jacoco"))
    testImplementation(project(":platform-native")) {
        because("BuildType from platform-native is used in ProjectAccessorsClassPathTest")
    }
    testImplementation(project(":plugins"))
    testImplementation(project(":version-control"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.ant)
    testImplementation(libs.mockitoKotlin)
    testImplementation(libs.jacksonKotlin)
    testImplementation(libs.archunit)
    testImplementation(libs.kotlinCoroutines)
    testImplementation(libs.awaitility)

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
    testFixturesImplementation(project(":test-kit"))
    testFixturesImplementation(project(":internal-testing"))
    testFixturesImplementation(project(":internal-integ-testing"))

    testFixturesImplementation(testFixtures(project(":hashing")))

    testFixturesImplementation(libs.junit)
    testFixturesImplementation(libs.mockitoKotlin)
    testFixturesImplementation(libs.jacksonKotlin)
    testFixturesImplementation(libs.asm)

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}

classycle {
    excludePatterns.add("org/gradle/ntcscript/**")
}

testFilesCleanup.reportOnly.set(true)
