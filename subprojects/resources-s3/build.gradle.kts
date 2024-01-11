plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation for interacting with S3 repositories"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":core-api"))
    implementation(project(":core"))
    implementation(project(":resources"))
    implementation(project(":resources-http"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.awsS3Core)
    implementation(libs.awsS3S3)
    implementation(libs.awsS3Kms) {
        because("Loaded by the AWS libraries with reflection when present")
    }
    implementation(libs.awsS3Sts) {
        because("Loaded by the AWS libraries with reflection when present: https://github.com/gradle/gradle/issues/15332")
    }
    implementation(libs.jaxbImpl)
    implementation(libs.commonsHttpclient)
    implementation(libs.commonsLang)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":ivy")))
    testImplementation(testFixtures(project(":maven")))

    integTestImplementation(project(":logging"))
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.groovyXml)
    integTestImplementation(libs.littleproxy)
    integTestImplementation(libs.jetty)

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}
