plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Adds support for generating parsers from Antlr grammars."

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":plugins"))
    implementation(project(":platform-jvm"))
    implementation(project(":workers"))
    implementation(project(":files"))
    implementation(project(":file-collections"))

    implementation(libs.slf4jApi)
    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.inject)

    compileOnly("antlr:antlr:2.7.7") {
        because("this dependency is downloaded by the antlr plugin")
    }

    testImplementation(project(":base-services-groovy"))
    testImplementation(project(":file-collections"))
    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))
}

packageCycles {
    excludePatterns.add("org/gradle/api/plugins/antlr/internal/*")
}
