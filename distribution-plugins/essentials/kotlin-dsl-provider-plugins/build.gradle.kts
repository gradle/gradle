plugins {
    id("gradlebuild.distribution.implementation-kotlin")
}

description = "Kotlin DSL Provider Plugins"

dependencies {
    implementation("org.gradle:kotlin-dsl")

    implementation("org.gradle:base-services")
    implementation("org.gradle:logging")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:resources")
    implementation("org.gradle:snapshots")
    implementation("org.gradle:tooling-api")
    implementation(project(":plugins"))
    implementation(project(":plugin-development"))

    implementation(libs.futureKotlin("scripting-compiler-impl-embeddable")) {
        isTransitive = false
    }

    implementation(libs.slf4jApi)
    implementation(libs.inject)

    testImplementation(testFixtures("org.gradle:kotlin-dsl"))
    testImplementation(libs.mockitoKotlin2)
}

classycle {
    excludePatterns.add("org/gradle/kotlin/dsl/provider/plugins/precompiled/tasks/**")
}
