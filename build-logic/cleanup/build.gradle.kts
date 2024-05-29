plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
    id("gradlebuild.build-logic.groovy-dsl-gradle-plugin")
}

description = "Provides a plugin that cleans up after executing tests"

errorprone {
    disabledChecks.addAll(
        "CatchAndPrintStackTrace", // 1 occurrences
        "DefaultCharset", // 3 occurrences
        "JavaTimeDefaultTimeZone", // 1 occurrences
        "StringCaseLocaleUsage", // 2 occurrences
    )
}

dependencies {
    implementation("gradlebuild:basics")
    implementation("gradlebuild:module-identity")
}
