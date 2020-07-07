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

    implementation(libs.slf4j_api)
    implementation(libs.guava)
    implementation(libs.nativePlatform)
    implementation(libs.awsS3_core)
    implementation(libs.awsS3_s3)
    implementation(libs.awsS3_kms)
    implementation(libs.jaxb)
    implementation(libs.jackson_core)
    implementation(libs.jackson_annotations)
    implementation(libs.jackson_databind)
    implementation(libs.commons_httpclient)
    implementation(libs.joda)
    implementation(libs.commons_lang)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependencyManagement")))
    testImplementation(testFixtures(project(":ivy")))
    testImplementation(testFixtures(project(":maven")))

    integTestImplementation(project(":logging"))
    integTestImplementation(libs.commons_io)
    integTestImplementation(libs.littleproxy)
    integTestImplementation(libs.jetty)

    integTestDistributionRuntimeOnly(project(":distributionsBasics"))
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
