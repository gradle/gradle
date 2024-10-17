plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation for interacting with S3 repositories"

errorprone {
    disabledChecks.addAll(
        "NotJavadoc", // 1 occurrences
    )
}

dependencies {
    api(projects.serviceProvider)
    api(projects.core)
    api(projects.coreApi)
    api(projects.resources)
    api(projects.resourcesHttp)

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
    implementation(projects.hashing)

    implementation(libs.commonsLang)
    implementation(libs.slf4jApi)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.dependencyManagement))
    testImplementation(testFixtures(projects.ivy))
    testImplementation(testFixtures(projects.maven))

    integTestImplementation(projects.logging)
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.groovyXml)
    integTestImplementation(libs.littleproxy)
    integTestImplementation(libs.jetty)

    integTestDistributionRuntimeOnly(projects.distributionsBasics)
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
tasks.isolatedProjectsIntegTest {
    enabled = false
}
