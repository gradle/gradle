plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":files"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":worker-processes"))
    implementation(project(":persistent-cache"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":workers"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-java"))
    implementation(project(":language-jvm"))

    implementation(libs.groovy) // for 'Task.property(String propertyName) throws groovy.lang.MissingPropertyException'
    implementation(libs.ant)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.inject)

    testImplementation(project(":file-collections"))
    testImplementation(project(":files"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":platform-base")))
    testImplementation(testFixtures(project(":plugins")))

    integTestImplementation(libs.commonsLang)
    integTestImplementation(libs.ant)

    testFixturesApi(testFixtures(project(":language-jvm")))
    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":model-core"))
    testFixturesImplementation(project(":platform-base"))
    testFixturesImplementation(testFixtures(project(":language-jvm")))

    compileOnly("org.scala-sbt:zinc_2.12:1.3.5")

    testRuntimeOnly(project(":distributions-jvm")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

strictCompile {
    ignoreDeprecations() // uses deprecated software model types
}

classycle {
    excludePatterns.set(listOf(
        "org/gradle/api/internal/tasks/scala/**",
        "org/gradle/language/scala/internal/toolchain/**"))
}

