plugins {
    id("gradlebuild.distribution.implementation-kotlin")
}

description = "Kotlin DSL Provider Plugins"

dependencies {
    implementation(project(":kotlin-dsl"))

    implementation(project(":base-services"))
    implementation(project(":core"))
    implementation(project(":core-api"))
    implementation(project(":functional"))
    implementation(project(":execution"))
    implementation(project(":file-collections"))
    implementation(project(":language-jvm"))
    implementation(project(":logging"))
    implementation(project(":model-core"))
    implementation(project(":plugin-development"))
    implementation(project(":plugins-java"))
    implementation(project(":platform-jvm"))
    implementation(project(":resources"))
    implementation(project(":snapshots"))
    implementation(project(":tooling-api"))
    implementation(project(":toolchains-jvm"))

    implementation(libs.futureKotlin("scripting-compiler-impl-embeddable")) {
        isTransitive = false
    }
    implementation(libs.futureKotlin("compiler-embeddable"))

    implementation(libs.groovy)
    implementation(libs.slf4jApi)
    implementation(libs.inject)

    testImplementation(testFixtures(project(":kotlin-dsl")))
    testImplementation(libs.mockitoKotlin2)
}

packageCycles {
    excludePatterns.add("org/gradle/kotlin/dsl/provider/plugins/precompiled/tasks/**")
}
