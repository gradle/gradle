plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins for building Scala code with Gradle."

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":worker-processes"))
    implementation(project(":persistent-cache"))
    implementation(project(":files"))
    implementation(project(":file-collections"))
    implementation(project(":file-temp"))
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
    implementation(project(":toolchains-jvm"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.inject)

    compileOnly("org.scala-sbt:zinc_2.13:1.9.3") {
        // Because not needed and was vulnerable
        exclude(module="log4j-core")
        exclude(module="log4j-api")
    }

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

packageCycles {
    excludePatterns.add("org/gradle/api/internal/tasks/scala/**")
    excludePatterns.add("org/gradle/api/tasks/*")
    excludePatterns.add("org/gradle/api/tasks/scala/internal/*")
    excludePatterns.add("org/gradle/language/scala/tasks/*")
}

integTest.usesJavadocCodeSnippets = true

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
