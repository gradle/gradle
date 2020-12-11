plugins {
    id("gradlebuild.distribution.implementation-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:messaging")
    implementation("org.gradle:native")
    implementation("org.gradle:logging")
    implementation("org.gradle:files")
    implementation("org.gradle:file-temp")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:persistent-cache")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:base-services-groovy")
    implementation("org.gradle:build-cache")
    implementation("org.gradle:core")
    implementation("org.gradle:resources")
    implementation("org.gradle:snapshots")
    implementation("org.gradle:execution")

    implementation(project(":resources-http"))
    implementation(project(":security"))

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

    testImplementation("org.gradle:process-services")
    testImplementation("org.gradle:build-cache-packaging")
    testImplementation(project(":diagnostics"))
    testImplementation(libs.asmUtil)
    testImplementation(libs.commonsHttpclient)
    testImplementation(libs.nekohtml)
    testImplementation(libs.groovyXml)
    testImplementation(testFixtures("org.gradle:base-services"))
    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures("org.gradle:messaging"))
    testImplementation(testFixtures("org.gradle:core-api"))
    testImplementation(testFixtures("org.gradle:snapshots"))
    testImplementation(testFixtures("org.gradle:execution"))
    testImplementation(testFixtures(project(":version-control")))
    testImplementation(testFixtures(project(":resources-http")))

    integTestImplementation("org.gradle:build-option")
    integTestImplementation(libs.jansi)
    integTestImplementation(libs.ansiControlSequenceUtil)
    integTestImplementation(libs.groovyJson)
    integTestImplementation(testFixtures("org.gradle:model-core"))
    integTestImplementation(testFixtures("org.gradle:security"))

    testFixturesApi("org.gradle:base-services") {
        because("Test fixtures export the Action class")
    }
    testFixturesApi("org.gradle:persistent-cache") {
        because("Test fixtures export the CacheAccess class")
    }

    testFixturesApi(libs.jetty)
    testFixturesImplementation("org.gradle:core")
    testFixturesImplementation(testFixtures("org.gradle:core"))
    testFixturesImplementation(testFixtures(project(":resources-http")))
    testFixturesImplementation("org.gradle:core-api")
    testFixturesImplementation("org.gradle:messaging")
    testFixturesImplementation("org.gradle:internal-integ-testing")
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
    testFixturesImplementation("org.gradle:jvm-services") {
        because("Groovy compiler bug leaks internals")
    }
    testFixturesImplementation(libs.jettyWebApp) {
        because("Groovy compiler bug leaks internals")
    }

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly("org.gradle:distributions-basics")
    crossVersionTestDistributionRuntimeOnly(project(":distributions-core"))
    crossVersionTestImplementation(libs.jettyWebApp)
}

classycle {
    excludePatterns.add("org/gradle/**")
}

testFilesCleanup.reportOnly.set(true)

tasks.clean {
    val testFiles = layout.buildDirectory.dir("tmp/test files")
    doFirst {
        // On daemon crash, read-only cache tests can leave read-only files around.
        // clean now takes care of those files as well
        testFiles.get().asFileTree.matching {
            include("**/read-only-cache/**")
        }.visit { this.file.setWritable(true) }
    }
}
