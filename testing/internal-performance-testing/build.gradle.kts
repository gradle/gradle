import gradlebuild.basics.googleApisJs

plugins {
    id("gradlebuild.internal.java")
}

description = "Collection of test fixtures for performance tests, internal use only"

sourceSets {
    main {
        // Incremental Groovy joint-compilation doesn't work with the Error Prone annotation processor
        errorprone.enabled = false
    }
}

val reports by configurations.creating
val flamegraph by configurations.creating
configurations.compileOnly { extendsFrom(flamegraph) }

repositories {
    googleApisJs()
}

dependencies {
    reports(variantOf(libs.jquery) { artifactType("js") })
    reports(variantOf(testLibs.flot) { classifier("min"); artifactType("js") })

    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.internalIntegTesting)
    api(projects.internalTesting)
    api(projects.stdlibJavaExtensions)
    api(projects.persistentCache)
    api(projects.reportRendering)
    api(projects.time)
    api(projects.toolingApi)

    api(testLibs.gradleProfiler) { because("Consumers need to instantiate BuildMutators") }
    api(testLibs.gradleProfilerBuildAction)
    api(testLibs.gradleProfilerBuildOperationsMeasuring)
    api(libs.guava)
    api(libs.groovy)
    api(libs.jacksonAnnotations)
    api(libs.jatl)
    api(testLibs.jettyServer)
    api(testLibs.jettyWebApp)
    api(libs.jspecify)
    api(testLibs.junit)
    api(testLibs.spock)

    implementation(projects.classloaders)
    implementation(projects.concurrent)
    implementation(projects.core)
    implementation(projects.projectFeaturesApi)

    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(testLibs.commonsMath)
    implementation(libs.groovyJson)
    implementation(testLibs.hikariCP)
    implementation(libs.jacksonCore)
    implementation(libs.jacksonDatabind)
    implementation(testLibs.jettyUtil)
    implementation(testLibs.joptSimple)
    implementation(libs.slf4jApi)

    runtimeOnly(libs.jclToSlf4j)
    runtimeOnly(testLibs.jetty)
    runtimeOnly(testLibs.mySqlConnector)

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}

val reportResources = tasks.register<Copy>("reportResources") {
    from(reports)
    into(layout.buildDirectory.file("generated-resources/report-resources/org/gradle/reporting"))
}

sourceSets.main {
    output.dir(reportResources.map { it.destinationDir.parentFile.parentFile.parentFile })
}

tasks.jar {
    inputs.files(flamegraph)
        .withPropertyName("flamegraph")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    from(files(provider{ flamegraph.map { zipTree(it) } }))
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
