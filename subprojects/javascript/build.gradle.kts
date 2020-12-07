plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":reporting"))
    implementation(project(":plugins"))
    implementation(project(":workers"))
    implementation(project(":dependency-management")) // Required by JavaScriptExtension#getGoogleApisRepository()
    implementation(project(":language-java")) // Required by RhinoShellExec

    implementation(libs.groovy)
    implementation(libs.slf4jApi)
    implementation(libs.commonsIo)
    implementation(libs.inject)
    implementation(libs.rhino)
    implementation(libs.gson) // used by JsHint.coordinates
    implementation(libs.simple) // used by http package in envjs.coordinates

    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))
}

classycle {
    excludePatterns.add("org/gradle/plugins/javascript/coffeescript/**")
}
