plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":worker-processes"))
    implementation(project(":files"))
    implementation(project(":file-collections"))
    implementation(project(":file-temp"))
    implementation(project(":persistent-cache"))
    implementation(project(":jvm-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":workers"))
    implementation(project(":snapshots"))
    implementation(project(":execution"))
    implementation(project(":dependency-management"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-jvm"))
    implementation(project(":build-events"))
    implementation(project(":tooling-api"))

    implementation(libs.groovy)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.fastutil)
    implementation(libs.ant) // for 'ZipFile' and 'ZipEntry'
    implementation(libs.asm)
    implementation(libs.asmCommons)
    implementation(libs.inject)

    runtimeOnly(project(":java-compiler-plugin"))

    testImplementation(project(":base-services-groovy"))
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":platform-base")))

    testFixturesApi(testFixtures(project(":language-jvm")))
    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":model-core"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(project(":platform-base"))
    testFixturesImplementation(project(":persistent-cache"))
    testFixturesImplementation(libs.slf4jApi)

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder test (JavaLanguagePluginTest) loads services from a Gradle distribution.")
    }

    integTestDistributionRuntimeOnly(project(":distributions-core"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-basics"))
}

tasks.withType<Test>().configureEach {
    if (!javaVersion.isJava9Compatible) {
        val tools = javaLauncher.get().metadata.installationPath.file("lib/tools.jar")
        classpath.plus(tools)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(null as? Int)
    sourceCompatibility = "8"
    targetCompatibility = "8"
}

strictCompile {
    ignoreDeprecations() // this project currently uses many deprecated part from 'platform-jvm'
}

classycle {
    // These public packages have classes that are tangled with the corresponding internal package.
    excludePatterns.add("org/gradle/api/tasks/compile/**")
    excludePatterns.add("org/gradle/external/javadoc/**")
}


integTest.usesSamples.set(true)
