plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Adds support for generating parsers from Antlr grammars."

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 1 occurrences
        "Finally", // 1 occurrences
    )
}

dependencies {
    api(projects.javaLanguageExtensions)
    api(project(":core"))
    api(project(":core-api"))
    api(project(":files"))
    api(project(":model-core"))

    api(libs.inject)

    implementation(project(":base-services"))
    implementation(project(":platform-jvm"))
    implementation(project(":plugins-java-base"))
    implementation(project(":plugins-java-library"))

    implementation(libs.guava)
    implementation(libs.jsr305)
    implementation(libs.slf4jApi)

    compileOnly("antlr:antlr:2.7.7") {
        because("this dependency is downloaded by the antlr plugin")
    }

    runtimeOnly(project(":language-jvm"))
    runtimeOnly(project(":workers"))

    testImplementation(project(":base-services-groovy"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(project(":file-collections"))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))
}

packageCycles {
    excludePatterns.add("org/gradle/api/plugins/antlr/internal/*")
}
