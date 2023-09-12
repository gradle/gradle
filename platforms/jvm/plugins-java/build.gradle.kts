plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Contains a basic JVM plugin used to compile, test, and assemble Java source; often applied by other JVM plugins (though named java-base, jvm-base would be a more proper name); and the Java plugin itself."

dependencies {
    implementation(project(":base-services"))
    implementation(project(":core-api"))
    implementation(project(":core"))
    implementation(project(":dependency-management"))
    implementation(project(":diagnostics"))
    implementation(project(":execution"))
    implementation(project(":file-collections"))
    implementation(project(":language-groovy"))
    implementation(project(":language-java"))
    implementation(project(":language-jvm"))
    implementation(project(":logging"))
    implementation(project(":ivy"))
    implementation(project(":maven"))
    implementation(project(":model-core"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":plugins-jvm-test-suite-base"))
    implementation(project(":publish"))
    implementation(project(":reporting"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))
    implementation(project(":toolchains-jvm"))

    implementation(libs.ant)
    implementation(libs.commonsLang)
    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.inject)

    testImplementation(testFixtures(project(":core")))

    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

packageCycles {
    excludePatterns.add("org/gradle/api/plugins/**")
}

integTest.usesJavadocCodeSnippets.set(true)
