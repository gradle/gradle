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

    api(project(":core"))
    api(project(":internal-testing"))
    api(project(":java-language-extensions"))
    api(project(":time"))
    api(project(":tooling-api"))

    api(libs.gradleProfiler) {
        because("Consumers need to instantiate BuildMutators")
    }
    api(libs.jetty)
    api(libs.jettyWebApp)
    api(libs.jsr305)

    implementation(project(":base-services"))
    implementation(project(":concurrent"))
    implementation(project(":core-api"))
    implementation(project(":logging"))
    implementation(project(":logging-api"))
    implementation(project(":internal-integ-testing"))
    implementation(project(":persistent-cache"))
    implementation(project(":wrapper-shared"))

    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.commonsMath)
    implementation(libs.guava)
    implementation(libs.groovy)
    implementation(libs.groovyAnt)
    implementation(libs.groovyJson)
    implementation(libs.hikariCP)
    implementation(libs.jacksonAnnotations)
    implementation(libs.jacksonCore)
    implementation(libs.jacksonDatabind)
    implementation(libs.jatl)
    implementation(libs.jclToSlf4j)
    implementation(libs.jettyUtil)
    implementation(libs.joda)
    implementation(libs.joptSimple)
    implementation(libs.junit)
    implementation(libs.mina)
    implementation(libs.spock)

    compileOnly(libs.javaParser) {
        because("The Groovy compiler inspects the dependencies at compile time")
    }

    runtimeOnly(libs.mySqlConnector)

    integTestDistributionRuntimeOnly(project(":distributions-core"))
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
