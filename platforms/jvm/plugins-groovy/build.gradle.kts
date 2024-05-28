plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Contains plugins for building Groovy projects."

dependencies {
    api(libs.jsr305)
    api(libs.groovy)
    api(libs.inject)

    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":language-java"))
    api(project(":model-core"))
    api(project(":build-process-services"))

    implementation(projects.javaLanguageExtensions)
    implementation(project(":file-collections"))
    implementation(project(":language-groovy"))
    implementation(project(":language-jvm"))
    implementation(project(":logging"))
    implementation(project(":platform-jvm"))
    implementation(project(":plugins-java"))
    implementation(project(":plugins-java-base"))
    implementation(project(":reporting"))
    implementation(project(":toolchains-jvm"))
    implementation(project(":toolchains-jvm-shared"))

    implementation(libs.guava)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":language-groovy")))

    testRuntimeOnly(project(":distributions-jvm"))

    integTestImplementation(testFixtures(project(":plugins-java-base")))

    integTestDistributionRuntimeOnly(project(":distributions-full")) {
        because("The full distribution is required to run the GroovyToJavaConversionIntegrationTest")
    }
    crossVersionTestDistributionRuntimeOnly(project(":distributions-core"))
}

integTest.usesJavadocCodeSnippets.set(true)
