plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation of messaging between Gradle processes"

gradlebuildJava.usedInWorkers()

errorprone {
    disabledChecks.addAll(
        "DoubleBraceInitialization", // 1 occurrences
        "EmptyBlockTag", // 2 occurrences
        "IdentityHashMapUsage", // 2 occurrences
        "InputStreamSlowMultibyteRead", // 1 occurrences
        "MixedMutabilityReturnType", // 3 occurrences
        "ReferenceEquality", // 1 occurrences
        "StringCaseLocaleUsage", // 1 occurrences
        "ThreadPriorityCheck", // 1 occurrences
        "UnrecognisedJavadocTag", // 1 occurrences
    )
}

dependencies {
    api(projects.concurrent)
    api(projects.javaLanguageExtensions)
    api(projects.serialization)
    api(project(":base-services"))

    api(libs.jsr305)
    api(libs.slf4jApi)

    implementation(projects.io)
    implementation(project(":build-operations"))

    implementation(libs.guava)

    testImplementation(testFixtures(projects.serialization))
    testImplementation(testFixtures(project(":core")))

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(libs.slf4jApi)

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
