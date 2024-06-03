plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.jmh")
}

description = "Logging infrastructure"

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.javaLanguageExtensions)
    api(projects.serialization)
    api(projects.serviceProvider)
    api(projects.time)
    api(project(":base-services"))
    api(project(":build-operations"))
    api(project(":build-option"))
    api(project(":cli"))
    api(project(":enterprise-logging"))
    api(project(":enterprise-workers"))
    api(project(":logging-api"))
    api(project(":native"))
    api(project(":problems-api"))

    api(libs.jansi)
    api(libs.jsr305)
    api(libs.slf4jApi)

    implementation(projects.concurrent)
    implementation(projects.io)
    implementation(projects.messaging)

    implementation(libs.errorProneAnnotations)
    implementation(libs.julToSlf4j)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.guava)

    // GSon is not strictly required here but removing it moves the dependency in the distribution from lib to lib/plugins
    // TODO Check if this is an issue
    runtimeOnly(libs.gson)
    runtimeOnly(libs.jclToSlf4j)
    runtimeOnly(libs.log4jToSlf4j)

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
