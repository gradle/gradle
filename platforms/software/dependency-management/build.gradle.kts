plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = """This project contains most of the dependency management logic of Gradle:
    |* the resolution engine,
    |* how to retrieve and process dependencies and their metadata,
    |* the dependency locking and verification implementations.
    |
    |DSL facing APIs are to be found in 'core-api'""".trimMargin()

dependencies {
    implementation(project(":base-services"))
    implementation(project(":build-option"))
    implementation(project(":enterprise-operations"))
    implementation(project(":functional"))
    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":files"))
    implementation(project(":file-temp"))
    implementation(project(":file-collections"))
    implementation(project(":persistent-cache"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":base-services-groovy"))
    implementation(project(":build-cache"))
    implementation(project(":core"))
    implementation(project(":resources"))
    implementation(project(":resources-http"))
    implementation(project(":snapshots"))
    implementation(project(":execution"))
    implementation(project(":security"))
    implementation(project(":wrapper-shared"))

    implementation(libs.slf4jApi)
    implementation(libs.groovy)
    implementation(libs.asm)
    implementation(libs.asmCommons)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.httpcore)
    implementation(libs.inject)
    implementation(libs.gson)
    implementation(libs.ant)
    implementation(libs.ivy)
    implementation(libs.maven3SettingsBuilder)
    implementation(libs.fastutil)

    testImplementation(project(":process-services"))
    testImplementation(project(":diagnostics"))
    testImplementation(project(":build-cache-packaging"))
    testImplementation(libs.asmUtil)
    testImplementation(libs.commonsHttpclient)
    testImplementation(libs.jsoup)
    testImplementation(libs.groovyXml)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":core-api")))
    testImplementation(testFixtures(project(":version-control")))
    testImplementation(testFixtures(project(":resources-http")))
    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":snapshots")))
    testImplementation(testFixtures(project(":execution")))
    testImplementation(testFixtures(project(":problems-api")))

    integTestImplementation(project(":build-option"))
    integTestImplementation(libs.jansi)
    integTestImplementation(libs.ansiControlSequenceUtil)
    integTestImplementation(libs.groovyJson)
    integTestImplementation(libs.socksProxy) {
        because("SOCKS proxy not part of internal-integ-testing api, since it has limited usefulness, so must be explicitly depended upon")
    }
    integTestImplementation(testFixtures(project(":security")))
    integTestImplementation(testFixtures(project(":model-core")))

    testFixturesApi(project(":base-services")) {
        because("Test fixtures export the Action class")
    }
    testFixturesApi(project(":persistent-cache")) {
        because("Test fixtures export the CacheAccess class")
    }

    testFixturesApi(libs.jetty)
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation(testFixtures(project(":resources-http")))
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":messaging"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(libs.slf4jApi)
    testFixturesImplementation(libs.inject)
    testFixturesImplementation(libs.groovyJson)
    testFixturesImplementation(libs.guava) {
        because("Groovy compiler reflects on private field on TextUtil")
    }
    testFixturesImplementation(libs.bouncycastlePgp)
    testFixturesApi(libs.testcontainersSpock) {
        because("API because of Groovy compiler bug leaking internals")
    }
    testFixturesImplementation(project(":jvm-services")) {
        because("Groovy compiler bug leaks internals")
    }
    testFixturesImplementation(libs.jettyWebApp) {
        because("Groovy compiler bug leaks internals")
    }

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestImplementation(project(":launcher")) {
        because("Daemon fixtures need DaemonRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm")) {
        because("Need access to java platforms")
    }
    crossVersionTestDistributionRuntimeOnly(project(":distributions-core"))
    crossVersionTestImplementation(libs.jettyWebApp)
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}

testFilesCleanup.reportOnly = true

tasks.clean {
    val testFiles = layout.buildDirectory.dir("tmp/teŝt files")
    doFirst {
        // On daemon crash, read-only cache tests can leave read-only files around.
        // clean now takes care of those files as well
        testFiles.get().asFileTree.matching {
            include("**/read-only-cache/**")
        }.visit { this.file.setWritable(true) }
    }
}
