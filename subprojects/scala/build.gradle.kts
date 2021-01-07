plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":worker-processes"))
    implementation(project(":persistent-cache"))
    implementation(project(":files"))
    implementation(project(":file-collections"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":workers"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-jvm"))
    implementation(project(":language-java"))
    implementation(project(":plugins"))
    implementation(project(":reporting"))
    implementation(project(":dependency-management"))
    implementation(project(":process-services"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.inject)

    compileOnly("org.scala-sbt:zinc_2.12:1.3.5")

    testImplementation(project(":base-services-groovy"))
    testImplementation(project(":files"))
    testImplementation(project(":resources"))
    testImplementation(libs.slf4jApi)
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":plugins")))
    testImplementation(testFixtures(project(":language-jvm")))
    testImplementation(testFixtures(project(":language-java")))

    integTestImplementation(project(":jvm-services"))

    testFixturesImplementation(testFixtures(project(":language-jvm")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

classycle {
    excludePatterns.add("org/gradle/api/internal/tasks/scala/**")
    excludePatterns.add("org/gradle/api/tasks/*")
    excludePatterns.add("org/gradle/language/scala/tasks/*")
}

integTest.usesSamples.set(true)
