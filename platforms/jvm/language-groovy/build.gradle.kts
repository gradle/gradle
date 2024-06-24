plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Adds support for building Groovy projects"

errorprone {
    disabledChecks.addAll(
        "ModifyCollectionInEnhancedForLoop", // 1 occurrences
        "UnusedMethod", // 4 occurrences
        "UnusedVariable", // 1 occurrences
    )
}

dependencies {
    api(projects.serviceProvider)
    api(project(":build-option"))
    api(project(":core-api"))
    api(project(":core"))
    api(project(":files"))
    api(project(":file-temp"))
    api(project(":jvm-services"))
    api(project(":language-java"))
    api(project(":language-jvm"))
    api(project(":problems-api"))
    api(project(":platform-base"))
    api(project(":toolchains-jvm"))
    api(project(":toolchains-jvm-shared"))
    api(project(":workers"))
    api(project(":worker-main"))
    api(project(":build-process-services"))

    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.concurrent)
    implementation(projects.javaLanguageExtensions)
    implementation(project(":base-services"))
    implementation(project(":file-collections"))
    implementation(project(":logging"))
    implementation(project(":logging-api"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.asm)

    testImplementation(project(":base-services-groovy"))
    testImplementation(project(":internal-testing"))
    testImplementation(project(":resources"))
    testImplementation(testFixtures(project(":core")))

    testFixturesApi(testFixtures(project(":language-jvm")))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(testFixtures(project(":model-core")))
    testFixturesImplementation(libs.guava)

    integTestImplementation(testFixtures(project(":model-core")))
    integTestImplementation(libs.commonsLang)
    integTestImplementation(libs.javaParser) {
        because("The Groovy docs inspects the dependencies at compile time")
    }
    integTestImplementation(libs.nativePlatform) {
        because("Required for SystemInfo")
    }

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

packageCycles {
    excludePatterns.add("org/gradle/api/internal/tasks/compile/**")
    excludePatterns.add("org/gradle/api/tasks/javadoc/**")
}
