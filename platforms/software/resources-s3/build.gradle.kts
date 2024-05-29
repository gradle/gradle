plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation for interacting with S3 repositories"

errorprone {
    disabledChecks.addAll(
        "NotJavadoc", // 1 occurrences
        "StringCaseLocaleUsage", // 1 occurrences
        "UnusedMethod", // 2 occurrences
        "UnusedVariable", // 1 occurrences
    )
}

dependencies {
    api(projects.serviceProvider)
    api(project(":core"))
    api(project(":core-api"))
    api(project(":resources"))
    api(project(":resources-http"))

    api(libs.awsS3Core)
    api(libs.awsS3S3)
    api(libs.awsS3Kms) {
        because("Loaded by the AWS libraries with reflection when present")
    }
    api(libs.awsS3Sts) {
        because("Loaded by the AWS libraries with reflection when present: https://github.com/gradle/gradle/issues/15332")
    }
    api(libs.guava)

    implementation(projects.baseServices)
    implementation(project(":hashing"))

    implementation(libs.commonsLang)
    implementation(libs.slf4jApi)

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


dependencyAnalysis {
    issues {
        onUnusedDependencies() {
            // This need to exist to be loaded reflectively
            exclude(libs.awsS3Kms)
            exclude(libs.awsS3Sts)
        }
    }
}
