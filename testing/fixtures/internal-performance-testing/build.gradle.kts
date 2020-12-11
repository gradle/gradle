plugins {
    id("gradlebuild.internal.java")
}

val reports by configurations.creating
val flamegraph by configurations.creating
configurations.compileOnly { extendsFrom(flamegraph) }

dependencies {
    reports("jquery:jquery.min:3.5.1@js")
    reports("flot:flot:0.8.1:min@js")

    api(libs.gradleProfiler) {
        because("Consumers need to instantiate BuildMutators")
    }
    implementation(libs.javaParser) {
        because("The Groovy compiler inspects the dependencies at compile time")
    }

    api(libs.jettyWebApp)

    implementation("org.gradle:base-services")
    implementation("org.gradle:native")
    implementation("org.gradle:cli")
    implementation("org.gradle:logging")
    implementation("org.gradle:process-services")
    implementation("org.gradle:core-api")
    implementation("org.gradle:build-option")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:snapshots")
    implementation("org.gradle:resources")
    implementation("org.gradle:persistent-cache")
    implementation("org.gradle:jvm-services")
    implementation("org.gradle:wrapper")
    implementation(project(":internal-integ-testing"))

    implementation(libs.junit)
    implementation(libs.spock)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.guava)
    implementation(libs.groovy)
    implementation(libs.groovyAnt)
    implementation(libs.groovyJson)
    implementation(libs.hikariCP)
    implementation(libs.jacksonAnnotations)
    implementation(libs.jacksonCore)
    implementation(libs.jacksonDatabind)
    implementation(libs.slf4jApi)
    implementation(libs.joda)
    implementation(libs.jatl)
    implementation(libs.commonsHttpclient)
    implementation(libs.jsch)
    implementation(libs.commonsMath)
    implementation(libs.jclToSlf4j)
    implementation(libs.flightrecorder)
    implementation(libs.mina)
    implementation(libs.joptSimple)
    implementation(testFixtures("org.gradle:core"))
    implementation(testFixtures("org.gradle:tooling-api"))

    runtimeOnly(libs.mySqlConnector)

    integTestDistributionRuntimeOnly("org.gradle:distributions-core")
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
