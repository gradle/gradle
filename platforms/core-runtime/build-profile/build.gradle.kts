plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Provides high-level insights into a Gradle build (--profile)"

errorprone {
    disabledChecks.addAll(
        "DateFormatConstant", // 2 occurrences
        "ThreadLocalUsage", // 1 occurrences
        "UnnecessaryParentheses", // 1 occurrences
    )
}

dependencies {
    api(project(":base-annotations"))
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":enterprise-logging"))

    implementation(project(":logging"))
    implementation(project(":logging-api"))

    implementation(libs.guava)

    testImplementation(project(":internal-testing"))

    integTestImplementation(libs.jsoup)

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
