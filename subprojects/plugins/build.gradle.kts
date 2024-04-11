plugins {
    id("gradlebuild.distribution.api-java")
}

errorprone {
    disabledChecks.addAll(
        "UnusedMethod", // 7 occurrences
        "UnusedVariable", // 1 occurrences
    )
}

dependencies {
    api(project(":base-services"))
    api(project(":core"))

    implementation(projects.javaLanguageExtensions)
    implementation(project(":core-api"))
    implementation(project(":hashing"))
    implementation(project(":language-java"))
    implementation(project(":logging"))
    implementation(project(":model-core"))
    implementation(project(":persistent-cache"))
    implementation(project(":platform-jvm"))
    implementation(project(":plugins-groovy"))
    implementation(project(":plugins-java-base"))
    implementation(project(":plugins-java-library"))
    implementation(project(":publish"))
    implementation(project(":snapshots"))

    integTestImplementation(testFixtures(project(":enterprise-operations")))
    integTestImplementation(testFixtures(project(":language-java")))
    integTestImplementation(testFixtures(project(":model-core")))
    integTestImplementation(testFixtures(project(":plugins-java")))
    integTestImplementation(testFixtures(project(":plugins-java-base")))
    integTestImplementation(testFixtures(project(":resources-http")))

    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

description = """Provides some services and tasks used by plugins - this project is being emptied and new plugins should not be added here."""
