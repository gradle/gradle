plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Base plugin for the maven and ivy publish plugins. Defines the publishing extension."

errorprone {
    disabledChecks.addAll(
        "MixedMutabilityReturnType", // 5 occurrences
        "StringCaseLocaleUsage", // 1 occurrences
    )
}

dependencies {
    api(project(":dependency-management"))

    implementation(project(":base-services"))
    implementation(project(":functional"))
    implementation(project(":logging"))
    implementation(project(":file-collections"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":base-services-groovy")) // for 'Specs'

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.gson)
    implementation(libs.inject)

    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

integTest.usesJavadocCodeSnippets = true
