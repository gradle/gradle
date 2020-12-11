plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:core-api")
    implementation("org.gradle:core")
    implementation("org.gradle:resources")

    implementation("org.gradle:resources-http")

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
    implementation(libs.jacksonCore)
    implementation(libs.jacksonDatabind)
    implementation(libs.commonsHttpclient)
    implementation(libs.commonsLang)

    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures("org.gradle:dependency-management"))
    testImplementation(testFixtures("org.gradle:ivy"))
    testImplementation(testFixtures("org.gradle:maven"))

    integTestImplementation("org.gradle:logging")
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.groovyXml)
    integTestImplementation(libs.littleproxy)
    integTestImplementation(libs.jetty)

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}
