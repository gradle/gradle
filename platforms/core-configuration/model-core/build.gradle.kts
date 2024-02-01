plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.jmh")
}

description = "Implementation of configuration model types and annotation metadata handling (Providers, software model, conventions)"

errorprone {
    disabledChecks.addAll(
        "AnnotateFormatMethod", // 1 occurrences
        "EmptyBlockTag", // 3 occurrences
        "FormatString", // 1 occurrences
        "GetClassOnEnum", // 4 occurrences
        "HidingField", // 1 occurrences
        "IdentityHashMapUsage", // 1 occurrences
        "ImmutableEnumChecker", // 1 occurrences
        "InvalidParam", // 1 occurrences
        "MixedMutabilityReturnType", // 4 occurrences
        "MutablePublicArray", // 1 occurrences
        "NullableOptional", // 2 occurrences
        "OperatorPrecedence", // 5 occurrences
        "ReferenceEquality", // 3 occurrences
        "StringCaseLocaleUsage", // 13 occurrences
        "TypeParameterShadowing", // 2 occurrences
        "UndefinedEquals", // 2 occurrences
        "UnnecessaryLambda", // 1 occurrences
        "UnnecessaryParentheses", // 1 occurrences
        "UnnecessaryStringBuilder", // 1 occurrences
        "UnusedMethod", // 8 occurrences
        "UnusedTypeParameter", // 1 occurrences
        "UnusedVariable", // 20 occurrences
    )
}

dependencies {
    api(project(":core-api"))
    api(project(":problems-api"))
    api(project(":base-annotations"))
    api(project(":hashing"))
    api(project(":process-services"))
    api(project(":base-services"))
    api(project(":files"))
    api(project(":functional"))
    api(project(":logging"))
    api(project(":messaging"))
    api(project(":persistent-cache"))
    api(project(":snapshots"))

    api(libs.asm)
    api(libs.jsr305)
    api(libs.inject)
    api(libs.groovy)
    api(libs.guava)

    implementation(project(":base-services-groovy"))

    implementation(libs.futureKotlin("stdlib"))
    implementation(libs.slf4jApi)
    implementation(libs.commonsLang)
    implementation(libs.fastutil)

    testFixturesApi(testFixtures(project(":diagnostics")))
    testFixturesApi(testFixtures(project(":core")))
    testFixturesApi(project(":internal-integ-testing"))
    testFixturesImplementation(libs.guava)
    testFixturesImplementation(libs.groovyAnt)
    testFixturesImplementation(libs.groovyDatetime)
    testFixturesImplementation(libs.groovyDateUtil)

    testImplementation(project(":process-services"))
    testImplementation(project(":file-collections"))
    testImplementation(project(":native"))
    testImplementation(project(":resources"))
    testImplementation(testFixtures(project(":core-api")))

    integTestImplementation(project(":platform-base"))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-native")) {
        because("ModelRuleCachingIntegrationTest requires a rules implementation")
    }

    jmhImplementation(platform(project(":distributions-dependencies")))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

tasks.withType<JavaCompile>().configureEach {
    options.release = null
    sourceCompatibility = "8"
    targetCompatibility = "8"
}

integTest.usesJavadocCodeSnippets = true

packageCycles {
    excludePatterns.add("org/gradle/model/internal/core/**")
    excludePatterns.add("org/gradle/model/internal/inspect/**")
    excludePatterns.add("org/gradle/api/internal/tasks/**")
    excludePatterns.add("org/gradle/model/internal/manage/schema/**")
    excludePatterns.add("org/gradle/model/internal/type/**")
    excludePatterns.add("org/gradle/api/internal/plugins/*")
    // cycle between org.gradle.api.internal.provider and org.gradle.util.internal
    // (api.internal.provider -> ConfigureUtil, DeferredUtil -> api.internal.provider)
    excludePatterns.add("org/gradle/util/internal/*")
}
