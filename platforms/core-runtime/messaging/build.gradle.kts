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
        "UnnecessaryParentheses", // 2 occurrences
        "UnrecognisedJavadocTag", // 1 occurrences
    )
}

dependencies {
    api(project(":base-annotations"))
    api(project(":hashing"))
    api(project(":base-services"))

    api(libs.fastutil)
    api(libs.jsr305)
    api(libs.slf4jApi)

    implementation(project(":build-operations"))

    implementation(libs.guava)
    implementation(libs.kryo)

    testImplementation(testFixtures(project(":core")))

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(libs.slf4jApi)

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
