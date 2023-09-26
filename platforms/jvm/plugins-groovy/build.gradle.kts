plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Contains plugins for building Groovy projects."

dependencies {
    implementation(project(":base-services"))
    implementation(project(":core-api"))
    implementation(project(":core"))
    implementation(project(":file-collections"))
    implementation(project(":language-groovy"))
    implementation(project(":language-java"))
    implementation(project(":language-jvm"))
    implementation(project(":logging"))
    implementation(project(":model-core"))
    implementation(project(":platform-jvm"))
    implementation(project(":plugins-java"))
    implementation(project(":reporting"))
    implementation(project(":toolchains-jvm"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.inject)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":language-groovy")))

    testRuntimeOnly(project(":distributions-jvm"))

    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

integTest.usesJavadocCodeSnippets.set(true)
