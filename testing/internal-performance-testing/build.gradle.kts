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
    reports("jquery:jquery.min:3.5.1@js")
    reports("flot:flot:0.8.1:min@js")

    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.internalIntegTesting)
    api(projects.internalTesting)
    api(projects.stdlibJavaExtensions)
    api(projects.logging)
    api(projects.persistentCache)
    api(projects.reportRendering)
    api(projects.time)
    api(projects.toolingApi)

    api(libs.gradleProfiler) {
        because("Consumers need to instantiate BuildMutators")
    }
    api(libs.guava)
    api(libs.groovy)
    api(libs.jacksonAnnotations)
    api(libs.jatl)
    api(libs.jettyServer)
    api(libs.jettyWebApp)
    api(libs.jsr305)
    api(libs.junit)
    api(libs.spock)

    implementation(projects.concurrent)
    implementation(projects.wrapperShared)

    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.commonsMath)
    implementation(projects.core)
    implementation(libs.groovyAnt)
    implementation(libs.groovyJson)
    implementation(libs.hikariCP)
    implementation(libs.jacksonCore)
    implementation(libs.jacksonDatabind)
    implementation(libs.jettyUtil)
    implementation(libs.joda)
    implementation(libs.joptSimple)
    implementation(libs.mina)
    implementation(libs.slf4jApi)

    compileOnly(libs.javaParser) {
        because("The Groovy compiler inspects the dependencies at compile time")
    }

    runtimeOnly(libs.jclToSlf4j)
    runtimeOnly(libs.jetty)
    runtimeOnly(libs.mySqlConnector)

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
