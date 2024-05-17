plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Contains the Java plugin, and its supporting classes.  This plugin is used as the basis for building a Java library or application by more specific plugins, and is sometimes applied by other JVM language projects."

dependencies {
    api(project(":core"))
    api(project(":core-api"))
    api(project(":language-java"))
    api(project(":plugins-jvm-test-suite"))
    api(project(":publish"))

    api(libs.inject)

    implementation(projects.javaLanguageExtensions)
    implementation(project(":base-services"))
    implementation(project(":diagnostics"))
    implementation(project(":execution"))
    implementation(project(":language-jvm"))
    implementation(project(":ivy"))
    implementation(project(":maven"))
    implementation(project(":model-core"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":plugins-java-base"))
    implementation(project(":testing-jvm"))
    implementation(project(":test-suites-base"))

    runtimeOnly(project(":dependency-management"))
    runtimeOnly(project(":testing-base"))
    runtimeOnly(project(":toolchains-jvm"))

    runtimeOnly(libs.groovy)

    testImplementation(testFixtures(project(":core")))

    integTestImplementation(testFixtures(projects.messaging))
    integTestImplementation(testFixtures(project(":enterprise-operations")))
    integTestImplementation(testFixtures(project(":language-java")))
    integTestImplementation(testFixtures(project(":language-jvm")))
    integTestImplementation(testFixtures(project(":plugins-java-base")))
    integTestImplementation(testFixtures(project(":plugins-jvm-test-fixtures")))
    integTestImplementation(testFixtures(project(":workers")))

    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-full"))
}

packageCycles {
    excludePatterns.add("org/gradle/api/plugins/**")
}
