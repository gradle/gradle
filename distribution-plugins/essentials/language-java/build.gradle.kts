plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:messaging")
    implementation("org.gradle:logging")
    implementation("org.gradle:process-services")
    implementation("org.gradle:worker-processes")
    implementation("org.gradle:files")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:file-temp")
    implementation("org.gradle:persistent-cache")
    implementation("org.gradle:jvm-services")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")
    implementation("org.gradle:snapshots")
    implementation("org.gradle:execution")
    implementation("org.gradle:build-events")
    implementation("org.gradle:tooling-api")
    implementation(project(":workers"))
    implementation(project(":dependency-management"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-jvm"))

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

    testImplementation("org.gradle:base-services-groovy")
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures(project(":platform-base")))

    testFixturesApi(testFixtures(project(":language-jvm")))
    testFixturesImplementation("org.gradle:base-services")
    testFixturesImplementation("org.gradle:core")
    testFixturesImplementation("org.gradle:core-api")
    testFixturesImplementation("org.gradle:model-core")
    testFixturesImplementation("org.gradle:persistent-cache")
    testFixturesImplementation(project(":platform-base"))
    testFixturesImplementation("org.gradle:internal-integ-testing")
    testFixturesImplementation(libs.slf4jApi)

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder test (JavaLanguagePluginTest) loads services from a Gradle distribution.")
    }

    integTestDistributionRuntimeOnly(project(":distributions-core"))
    crossVersionTestDistributionRuntimeOnly("org.gradle:distributions-basics")
}

tasks.withType<Test>().configureEach {
    if (!javaVersion.isJava9Compatible) {
        classpath += javaLauncher.get().metadata.installationPath.files("lib/tools.jar")
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
