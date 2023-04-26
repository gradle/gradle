plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Logging infrastructure"

gradlebuildJava.usedInWorkers()

dependencies {
    api(project(":logging-api"))
    api(project(":enterprise-logging"))

    implementation(project(":base-services"))
    implementation(project(":enterprise-workers"))
    implementation(project(":messaging"))
    implementation(project(":cli"))
    implementation(project(":build-option"))

    implementation(project(":native"))
    implementation(libs.julToSlf4j)
    implementation(libs.ant)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.guava)
    implementation(libs.jansi)

    runtimeOnly(libs.log4jToSlf4j)
    runtimeOnly(libs.jclToSlf4j)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":testing-jvm")))
    testImplementation(libs.groovyDatetime)
    testImplementation(libs.groovyDateUtil)

    integTestImplementation(libs.ansiControlSequenceUtil)

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":enterprise-workers"))
    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation(libs.slf4jApi)

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

packageCycles {
    excludePatterns.add("org/gradle/internal/featurelifecycle/**")
    excludePatterns.add("org/gradle/util/**")
}

tasks {
    // Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
    configCacheIntegTest {
        systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
    }

    // Write Kotlin DSL versions manifest, so Kotlin version is available similarly as GradleVersion
    val writeVersionsManifest by registering(WriteProperties::class) {
        destinationFile = layout.buildDirectory.file("versionsManifest/gradle-kotlin-dsl-versions.properties")
        property("kotlin", libs.kotlinVersion)
    }

    processResources {
        from(writeVersionsManifest)
    }
}
