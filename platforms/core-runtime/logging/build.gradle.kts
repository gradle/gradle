plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.jmh")
}

description = "Logging infrastructure"

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.serialization)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.time)
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.buildOption)
    api(projects.cli)
    api(projects.enterpriseLogging)
    api(projects.enterpriseWorkers)
    api(projects.loggingApi)
    api(projects.native)
    api(projects.problemsApi)

    api(libs.jansi)
    api(libs.jsr305)
    api(libs.slf4jApi)

    implementation(projects.concurrent)
    implementation(projects.io)
    implementation(projects.messaging)
    implementation(projects.serviceRegistryBuilder)

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

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.testingJvm))
    testImplementation(libs.groovyDatetime)
    testImplementation(libs.groovyDateUtil)

    integTestImplementation(libs.ansiControlSequenceUtil)

    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.enterpriseWorkers)
    testFixturesImplementation(testFixtures(projects.core))
    testFixturesImplementation(libs.slf4jApi)

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}

packageCycles {
    excludePatterns.add("org/gradle/internal/featurelifecycle/**")
    excludePatterns.add("org/gradle/util/**")
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
