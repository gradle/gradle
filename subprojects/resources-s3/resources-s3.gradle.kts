import gradlebuild.cleanup.WhenNotEmpty

plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":resources"))
    implementation(project(":resourcesHttp"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.nativePlatform)
    implementation(libs.awsS3Core)
    implementation(libs.awsS3S3)
    implementation(libs.awsS3Kms)
    implementation(libs.jaxb)
    implementation(libs.jacksonCore)
    implementation(libs.jacksonAnnotations)
    implementation(libs.jacksonDatabind)
    implementation(libs.commonsHttpclient)
    implementation(libs.joda)
    implementation(libs.commonsLang)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependencyManagement")))
    testImplementation(testFixtures(project(":ivy")))
    testImplementation(testFixtures(project(":maven")))

    integTestImplementation(project(":logging"))
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.littleproxy)
    integTestImplementation(libs.jetty)

    integTestDistributionRuntimeOnly(project(":distributionsBasics"))
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
