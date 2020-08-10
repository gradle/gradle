plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":files"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":workerProcesses"))
    implementation(project(":persistentCache"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":workers"))
    implementation(project(":platformBase"))
    implementation(project(":platformJvm"))
    implementation(project(":languageJava"))
    implementation(project(":languageJvm"))

    implementation(libs.groovy) // for 'Task.property(String propertyName) throws groovy.lang.MissingPropertyException'
    implementation(libs.ant)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.inject)

    testImplementation(project(":fileCollections"))
    testImplementation(project(":files"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":platformBase")))
    testImplementation(testFixtures(project(":plugins")))

    integTestImplementation(libs.commonsLang)
    integTestImplementation(libs.ant)

    testFixturesApi(testFixtures(project(":languageJvm")))
    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":coreApi"))
    testFixturesImplementation(project(":modelCore"))
    testFixturesImplementation(project(":platformBase"))
    testFixturesImplementation(testFixtures(project(":languageJvm")))

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

